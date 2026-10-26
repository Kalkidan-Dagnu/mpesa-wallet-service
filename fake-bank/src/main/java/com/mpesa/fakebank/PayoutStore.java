package com.mpesa.fakebank;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory registry of all payouts seen by this fake bank.
 *
 * ── Idempotency contract ─────────────────────────────────────────────────────
 * register() uses computeIfAbsent so the first call for a given payout_id
 * atomically stores the record; all subsequent calls return the existing one.
 * This means the same payout_id always gets the same outcome, bank_ref, and
 * event_id — the bank never pays twice for the same id.
 *
 * ── Amount-mismatch detection ────────────────────────────────────────────────
 * If a known payout_id is resubmitted with a different amount, register()
 * returns null. The controller must handle this by returning REJECTED with
 * reason AMOUNT_MISMATCH.
 *
 * ── total-paid tracking ──────────────────────────────────────────────────────
 * totalPaid is a BigDecimal held in an AtomicReference. It is incremented
 * atomically (CAS loop) each time a payout transitions to SUCCEEDED.
 * Tests can read GET /bank/total-paid to reconcile against the wallet ledger.
 */
@Component
public class PayoutStore {

    private final ConcurrentHashMap<String, PayoutRecord> records = new ConcurrentHashMap<>();
    private final AtomicReference<BigDecimal> totalPaid = new AtomicReference<>(BigDecimal.ZERO);

    /**
     * Register a new payout or return the existing record for idempotency.
     *
     * @param payoutId the caller-supplied payout identifier
     * @param amount   the amount from this request (used for mismatch check)
     * @param factory  called only on the first occurrence to create the record
     * @return existing or newly created record, or {@code null} if the payout_id
     *         is known but the amount differs from the original submission
     */
    public PayoutRecord register(String payoutId, BigDecimal amount,
                                 java.util.function.Supplier<PayoutRecord> factory) {
        // computeIfAbsent is atomic: only one thread will call factory for a given key
        PayoutRecord record = records.computeIfAbsent(payoutId, k -> factory.get());

        // Amount mismatch on a known payout_id → signal rejection
        if (record.amount.compareTo(amount) != 0) {
            return null;  // caller treats this as REJECTED / AMOUNT_MISMATCH
        }
        return record;
    }

    /** Retrieve a record by payout_id, or null if not found. */
    public PayoutRecord get(String payoutId) {
        return records.get(payoutId);
    }

    /**
     * Record a successful payment. Called by the callback sender when
     * it has decided a payout SUCCEEDED (before sending the callback).
     * Thread-safe CAS loop ensures no amount is counted twice.
     */
    public void recordSucceeded(PayoutRecord record) {
        record.payoutStatus.set(PayoutRecord.PayoutStatus.SUCCEEDED);
        // CAS loop: accumulate amount into totalPaid atomically
        totalPaid.updateAndGet(current -> current.add(record.amount));
    }

    /**
     * Record a failed payment. Called by callback sender when outcome is FAILED.
     */
    public void recordFailed(PayoutRecord record) {
        record.payoutStatus.set(PayoutRecord.PayoutStatus.FAILED);
    }

    /** Sum of all amounts that have reached SUCCEEDED status. */
    public BigDecimal getTotalPaid() {
        return totalPaid.get();
    }
}
