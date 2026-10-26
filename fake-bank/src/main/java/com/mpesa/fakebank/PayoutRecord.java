package com.mpesa.fakebank;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory record for one payout.
 *
 * All fields are set once at creation time except {@code payoutStatus},
 * which transitions from IN_PROGRESS → SUCCEEDED or FAILED when the
 * callback fires (for accepted payouts).
 *
 * Thread safety: payoutStatus is an AtomicReference so the callback thread
 * can update it while the GET endpoint reads it without synchronization.
 * All other fields are effectively immutable after the record is stored.
 */
public class PayoutRecord {

    public enum InitialOutcome { ACCEPTED, REJECTED, NO_RESPONSE }
    public enum PayoutStatus   { IN_PROGRESS, SUCCEEDED, FAILED, REJECTED, NOT_FOUND }

    /** The payout_id supplied by the caller. */
    public final String payoutId;

    /** Amount exactly as submitted. Used for idempotency amount-mismatch check. */
    public final BigDecimal amount;

    /** Initial routing decision: ACCEPTED / REJECTED / NO_RESPONSE. */
    public final InitialOutcome initialOutcome;

    /**
     * Bank-assigned reference, generated once for ACCEPTED and processed
     * NO_RESPONSE payouts. Null for REJECTED and unprocessed NO_RESPONSE.
     */
    public final String bankRef;

    /**
     * Final status as visible via GET /bank/payouts/{id}.
     * Starts as IN_PROGRESS for accepted payouts, updated to SUCCEEDED or FAILED
     * when the callback is sent.  REJECTED immediately for rejected payouts.
     */
    public final AtomicReference<PayoutStatus> payoutStatus;

    /**
     * The pre-decided final outcome for payouts that will eventually callback.
     * Set at creation time so the callback sender uses the same roll result
     * rather than re-rolling. Null for REJECTED and unprocessed NO_RESPONSE.
     */
    public final PayoutStatus decidedFinalStatus;

    /**
     * event_id used for the callback. Generated once; reused for duplicate sends.
     * Null for payouts that never send a callback.
     */
    public final String eventId;

    /** True if this no-response payout was actually processed (callback follows). */
    public final boolean noResponseProcessed;

    public PayoutRecord(String payoutId, BigDecimal amount, InitialOutcome initialOutcome,
                        String bankRef, PayoutStatus initialStatus,
                        PayoutStatus decidedFinalStatus,
                        String eventId, boolean noResponseProcessed) {
        this.payoutId             = payoutId;
        this.amount               = amount;
        this.initialOutcome       = initialOutcome;
        this.bankRef              = bankRef;
        this.payoutStatus         = new AtomicReference<>(initialStatus);
        this.decidedFinalStatus   = decidedFinalStatus;
        this.eventId              = eventId;
        this.noResponseProcessed  = noResponseProcessed;
    }
}
