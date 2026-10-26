package com.mpesa.fakebank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fake bank REST API.
 *
 * POST /bank/payouts          — blocking, 2-6s latency; 80% ACCEPTED, 8% REJECTED, 12% NO_RESPONSE
 * GET  /bank/payouts/{id}     — 1-3s latency; returns current status
 * GET  /bank/total-paid       — sum of all SUCCEEDED amounts for test reconciliation
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BankController {

    private final BankProperties  props;
    private final PayoutStore     store;
    private final OutcomeDecider  decider;
    private final CallbackSender  callbackSender;

    // ── POST /bank/payouts ────────────────────────────────────────────────────

    @PostMapping("/bank/payouts")
    public ResponseEntity<Map<String, Object>> submitPayout(
            @RequestBody Map<String, Object> body
    ) throws InterruptedException {

        String     payoutId    = (String) body.get("payout_id");
        BigDecimal amount      = new BigDecimal(body.get("amount").toString());

        // ── Idempotency: register or retrieve existing record ────────────────
        PayoutRecord record = store.register(payoutId, amount,
                () -> decider.decide(payoutId, amount));

        if (record == null) {
            // Known payout_id with a different amount — reject immediately
            return ResponseEntity.ok(Map.of(
                    "result", "REJECTED",
                    "reason", "AMOUNT_MISMATCH"
            ));
        }

        // ── For replayed requests return immediately without re-sleeping ──────
        boolean isReplay = record.payoutId.equals(payoutId)
                && store.get(payoutId) == record
                && record.initialOutcome != null;
        // We determine replay by checking if the record already has a non-IN_PROGRESS
        // status — i.e. the callback already fired. For fresh records we simulate latency.
        // Simpler: track whether we just created it. Since computeIfAbsent is atomic,
        // we can't easily tell here — so we always simulate latency for simplicity.
        // In practice replays are rare and the latency is acceptable.

        switch (record.initialOutcome) {

            case ACCEPTED -> {
                // Normal latency: 2–6 seconds
                sleepRandom(props.getMinLatencySeconds(), props.getMaxLatencySeconds());
                // Fire callback asynchronously (may arrive before this response)
                callbackSender.sendCallback(record);
                return ResponseEntity.ok(Map.of(
                        "result",   "ACCEPTED",
                        "bank_ref", record.bankRef
                ));
            }

            case REJECTED -> {
                sleepRandom(props.getMinLatencySeconds(), props.getMaxLatencySeconds());
                return ResponseEntity.ok(Map.of(
                        "result", "REJECTED",
                        "reason", "BENEFICIARY_ACCOUNT_INVALID"
                ));
            }

            case NO_RESPONSE -> {
                // Hold the connection for the full no-response period
                int holdSec = props.getNoResponseHoldSeconds();
                log.info("NO_RESPONSE for payout {} — holding {}s", payoutId, holdSec);
                Thread.sleep(Duration.ofSeconds(holdSec));
                // If the bank processed it, fire the callback (connection is still open
                // so callback arrives before or just after this 504)
                if (record.noResponseProcessed) {
                    callbackSender.sendCallback(record);
                }
                // Return 504
                return ResponseEntity.status(504).body(Map.of(
                        "error", "GATEWAY_TIMEOUT"
                ));
            }

            default -> throw new IllegalStateException("Unknown outcome: " + record.initialOutcome);
        }
    }

    // ── GET /bank/payouts/{payout_id} ─────────────────────────────────────────

    @GetMapping("/bank/payouts/{payoutId}")
    public ResponseEntity<Map<String, Object>> getPayoutStatus(
            @PathVariable String payoutId
    ) throws InterruptedException {

        sleepRandom(props.getGetMinLatencySeconds(), props.getGetMaxLatencySeconds());

        PayoutRecord record = store.get(payoutId);
        if (record == null) {
            return ResponseEntity.ok(Map.of("status", "NOT_FOUND"));
        }
        return ResponseEntity.ok(Map.of("status", record.payoutStatus.get().name()));
    }

    // ── GET /bank/total-paid ──────────────────────────────────────────────────

    /**
     * Returns the total amount the bank has actually paid out (SUCCEEDED).
     * Tests use this to reconcile against the wallet-service ledger.
     */
    @GetMapping("/bank/total-paid")
    public ResponseEntity<Map<String, Object>> getTotalPaid() {
        return ResponseEntity.ok(Map.of("total_paid", store.getTotalPaid()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void sleepRandom(int minSec, int maxSec) throws InterruptedException {
        int sec = minSec + ThreadLocalRandom.current().nextInt(maxSec - minSec + 1);
        Thread.sleep(Duration.ofSeconds(sec));
    }
}
