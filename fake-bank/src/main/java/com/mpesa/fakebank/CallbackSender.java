package com.mpesa.fakebank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends callbacks to the wallet-service after a payout outcome is decided.
 *
 * ── Timing ──────────────────────────────────────────────────────────────────
 * Delay before first send: [callbackDelayMinSeconds, callbackDelayMaxSeconds].
 * This means a callback can arrive BEFORE the POST /bank/payouts call returns
 * (the payout handler blocks 2–6s; a callback with 1s delay fires in parallel).
 *
 * ── Duplicate callbacks ──────────────────────────────────────────────────────
 * 30% of callbacks are sent 2–3 times concurrently with the same event_id.
 * "Concurrently" means we spawn multiple threads for the same send round.
 *
 * ── Retry logic ──────────────────────────────────────────────────────────────
 * If the wallet-service does not respond with 2xx within callbackTimeoutMs,
 * we retry every callbackRetryIntervalSeconds, up to callbackMaxRetries times.
 *
 * ── Target selection ─────────────────────────────────────────────────────────
 * We pick a URL from callbackUrls at random for each send attempt, so both
 * wallet-service instances receive callback traffic over time.
 */
@Slf4j
@Component
public class CallbackSender {

    private final BankProperties props;
    private final PayoutStore    store;
    private final HttpClient     http;

    public CallbackSender(BankProperties props, PayoutStore store) {
        this.props = props;
        this.store = store;
        this.http  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Schedule and send the callback for a payout that has a decided outcome.
     * Runs on a separate thread so the POST /bank/payouts handler is not blocked.
     *
     * @param record the payout record (must have non-null eventId and decidedFinalStatus)
     */
    @Async
    public void sendCallback(PayoutRecord record) {
        try {
            // ── Wait the configured delay ─────────────────────────────────────
            int delayMin = props.getCallbackDelayMinSeconds();
            int delayMax = props.getCallbackDelayMaxSeconds();
            int delaySec = delayMin + ThreadLocalRandom.current().nextInt(delayMax - delayMin + 1);
            Thread.sleep(Duration.ofSeconds(delaySec));

            // ── Apply the pre-decided outcome ─────────────────────────────────
            if (record.decidedFinalStatus == PayoutRecord.PayoutStatus.SUCCEEDED) {
                store.recordSucceeded(record);
            } else {
                store.recordFailed(record);
            }

            // ── Determine how many times to send ─────────────────────────────
            int sends = 1;
            if (ThreadLocalRandom.current().nextDouble() < props.getDuplicateCallbackRate()) {
                // 30% chance: send 2 or 3 times concurrently
                sends = 2 + ThreadLocalRandom.current().nextInt(2);  // 2 or 3
            }

            String body = buildBody(record);

            // ── Send concurrently (for duplicates) ────────────────────────────
            List<Thread> senders = new java.util.ArrayList<>(sends);
            for (int i = 0; i < sends; i++) {
                Thread t = Thread.ofVirtual().start(() -> sendWithRetry(body, record));
                senders.add(t);
            }
            for (Thread t : senders) {
                t.join();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Callback sender interrupted for payout {}", record.payoutId);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void sendWithRetry(String body, PayoutRecord record) {
        int maxRetries = props.getCallbackMaxRetries();
        int intervalMs = props.getCallbackRetryIntervalSeconds() * 1000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(intervalMs); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
            }
            String url = pickUrl();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMillis(props.getCallbackTimeoutMs()))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.debug("Callback accepted by {} for payout {} (attempt {})",
                            url, record.payoutId, attempt + 1);
                    return;  // success — stop retrying
                }
                log.warn("Callback to {} returned {} for payout {} — retrying",
                        url, resp.statusCode(), record.payoutId);

            } catch (IOException | InterruptedException e) {
                log.warn("Callback to {} failed for payout {}: {} — retrying",
                        url, record.payoutId, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); return;
                }
            }
        }
        log.warn("Callback for payout {} exhausted {} retries", record.payoutId, maxRetries);
    }

    private String buildBody(PayoutRecord record) {
        String status = record.decidedFinalStatus == PayoutRecord.PayoutStatus.SUCCEEDED
                ? "SUCCEEDED" : "FAILED";
        // Inline JSON — no Jackson dependency needed for this simple structure
        return """
                {
                  "event_id":  "%s",
                  "payout_id": "%s",
                  "bank_ref":  "%s",
                  "status":    "%s"
                }""".formatted(record.eventId, record.payoutId,
                               record.bankRef != null ? record.bankRef : "",
                               status);
    }

    private String pickUrl() {
        List<String> urls = props.getCallbackUrls();
        return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
    }
}
