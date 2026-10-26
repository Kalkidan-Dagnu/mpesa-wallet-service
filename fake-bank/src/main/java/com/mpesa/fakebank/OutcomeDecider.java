package com.mpesa.fakebank;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides outcomes by rolling dice against the configured rates.
 *
 * All decisions are made once per payout at registration time and stored
 * in the PayoutRecord so replayed requests always return the same result.
 *
 * ── Outcome probability bands ────────────────────────────────────────────────
 * [0,                  acceptRate)           → ACCEPTED
 * [acceptRate,         acceptRate+rejectRate)→ REJECTED
 * [acceptRate+reject,  1.0)                  → NO_RESPONSE
 *
 * For ACCEPTED: a second roll decides SUCCEEDED vs FAILED.
 * For NO_RESPONSE: a third roll decides if the bank processed it (callback sent).
 */
@Component
public class OutcomeDecider {

    private final BankProperties props;

    public OutcomeDecider(BankProperties props) {
        this.props = props;
    }

    /**
     * Decide the full outcome for a brand-new payout and return a PayoutRecord.
     * The record is not yet stored in PayoutStore — the caller does that.
     */
    public PayoutRecord decide(String payoutId, BigDecimal amount) {
        double roll       = ThreadLocalRandom.current().nextDouble();
        double acceptRate = props.getAcceptRate();
        double rejectRate = props.getRejectRate();

        if (roll < acceptRate) {
            return buildAccepted(payoutId, amount);
        } else if (roll < acceptRate + rejectRate) {
            return buildRejected(payoutId, amount);
        } else {
            return buildNoResponse(payoutId, amount);
        }
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private PayoutRecord buildAccepted(String payoutId, BigDecimal amount) {
        PayoutRecord.PayoutStatus finalStatus = rollFinalStatus();
        return new PayoutRecord(
                payoutId, amount,
                PayoutRecord.InitialOutcome.ACCEPTED,
                generateBankRef(),
                PayoutRecord.PayoutStatus.IN_PROGRESS,  // current status
                finalStatus,                             // decided now, applied by callback
                UUID.randomUUID().toString(),
                false
        );
    }

    private PayoutRecord buildRejected(String payoutId, BigDecimal amount) {
        return new PayoutRecord(
                payoutId, amount,
                PayoutRecord.InitialOutcome.REJECTED,
                null,                                    // no bank_ref
                PayoutRecord.PayoutStatus.REJECTED,
                null,                                    // no final status change
                null,                                    // no callback
                false
        );
    }

    private PayoutRecord buildNoResponse(String payoutId, BigDecimal amount) {
        boolean processed = ThreadLocalRandom.current().nextDouble()
                < props.getNoResponseProcessedRate();
        if (processed) {
            PayoutRecord.PayoutStatus finalStatus = rollFinalStatus();
            return new PayoutRecord(
                    payoutId, amount,
                    PayoutRecord.InitialOutcome.NO_RESPONSE,
                    generateBankRef(),
                    PayoutRecord.PayoutStatus.IN_PROGRESS,
                    finalStatus,
                    UUID.randomUUID().toString(),
                    true   // processed — callback will follow
            );
        } else {
            // Bank never received it — no callback, status stays NOT_FOUND
            return new PayoutRecord(
                    payoutId, amount,
                    PayoutRecord.InitialOutcome.NO_RESPONSE,
                    null,
                    PayoutRecord.PayoutStatus.NOT_FOUND,
                    null,
                    null,
                    false
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Roll whether an accepted/processed payout ultimately FAILS or SUCCEEDS. */
    private PayoutRecord.PayoutStatus rollFinalStatus() {
        return ThreadLocalRandom.current().nextDouble() < props.getCallbackFailRate()
                ? PayoutRecord.PayoutStatus.FAILED
                : PayoutRecord.PayoutStatus.SUCCEEDED;
    }

    /** Generates a bank reference like "BR4F830B87B25B62A5". */
    private String generateBankRef() {
        byte[] bytes = new byte[9];
        ThreadLocalRandom.current().nextBytes(bytes);
        return "BR" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
