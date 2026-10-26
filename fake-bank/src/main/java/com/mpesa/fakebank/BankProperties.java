package com.mpesa.fakebank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * All fake-bank behaviour rates, bound from application.properties (prefix "bank").
 *
 * Every value has a sensible default matching the contract spec, but can be
 * overridden in application.properties or via environment variables without
 * a code change.
 *
 * Example: force 100% no-response in a test:
 *   bank.accept-rate=0.0
 *   bank.reject-rate=0.0
 *
 * Example: force 100% duplicate callbacks:
 *   bank.duplicate-callback-rate=1.0
 */
@Component
@ConfigurationProperties(prefix = "bank")
public class BankProperties {

    // ── Outcome rates ─────────────────────────────────────────────────────────

    /** Probability POST returns ACCEPTED (money will move). */
    private double acceptRate = 0.80;

    /** Probability POST returns REJECTED (final, no callback). */
    private double rejectRate = 0.08;

    // no-response rate is implicit: 1 - acceptRate - rejectRate

    /** Of no-response payouts, fraction the bank actually processed (callback follows). */
    private double noResponseProcessedRate = 0.50;

    /** Of accepted payouts, fraction that ultimately FAIL (rest SUCCEED). */
    private double callbackFailRate = 0.05;

    // ── Callback behaviour ────────────────────────────────────────────────────

    /** Fraction of accepted payouts whose callback is sent 2–3 times concurrently. */
    private double duplicateCallbackRate = 0.30;

    /** Minimum delay before first callback send (seconds). */
    private int callbackDelayMinSeconds = 1;

    /** Maximum delay before first callback send (seconds). */
    private int callbackDelayMaxSeconds = 10;

    /** HTTP timeout when sending a callback (ms). No 2xx within this → retry. */
    private int callbackTimeoutMs = 2000;

    /** Maximum retry attempts after initial send. */
    private int callbackMaxRetries = 5;

    /** Seconds between retry attempts. */
    private int callbackRetryIntervalSeconds = 3;

    /** Callback target URLs — bank picks one at random per send. */
    private List<String> callbackUrls = List.of(
            "http://localhost:8081/mpesa/v1/callbacks/bank",
            "http://localhost:8082/mpesa/v1/callbacks/bank"
    );

    // ── Latency simulation ────────────────────────────────────────────────────

    /** Min latency for POST /bank/payouts normal response (seconds). */
    private int minLatencySeconds = 2;

    /** Max latency for POST /bank/payouts normal response (seconds). */
    private int maxLatencySeconds = 6;

    /** How long to hold a no-response connection before returning 504 (seconds). */
    private int noResponseHoldSeconds = 60;

    /** Min latency for GET /bank/payouts/{id} (seconds). */
    private int getMinLatencySeconds = 1;

    /** Max latency for GET /bank/payouts/{id} (seconds). */
    private int getMaxLatencySeconds = 3;

    // ── Getters and setters ───────────────────────────────────────────────────

    public double getAcceptRate()                 { return acceptRate; }
    public void   setAcceptRate(double v)         { acceptRate = v; }

    public double getRejectRate()                 { return rejectRate; }
    public void   setRejectRate(double v)         { rejectRate = v; }

    public double getNoResponseProcessedRate()    { return noResponseProcessedRate; }
    public void   setNoResponseProcessedRate(double v) { noResponseProcessedRate = v; }

    public double getCallbackFailRate()           { return callbackFailRate; }
    public void   setCallbackFailRate(double v)   { callbackFailRate = v; }

    public double getDuplicateCallbackRate()      { return duplicateCallbackRate; }
    public void   setDuplicateCallbackRate(double v) { duplicateCallbackRate = v; }

    public int    getCallbackDelayMinSeconds()    { return callbackDelayMinSeconds; }
    public void   setCallbackDelayMinSeconds(int v) { callbackDelayMinSeconds = v; }

    public int    getCallbackDelayMaxSeconds()    { return callbackDelayMaxSeconds; }
    public void   setCallbackDelayMaxSeconds(int v) { callbackDelayMaxSeconds = v; }

    public int    getCallbackTimeoutMs()          { return callbackTimeoutMs; }
    public void   setCallbackTimeoutMs(int v)     { callbackTimeoutMs = v; }

    public int    getCallbackMaxRetries()         { return callbackMaxRetries; }
    public void   setCallbackMaxRetries(int v)    { callbackMaxRetries = v; }

    public int    getCallbackRetryIntervalSeconds() { return callbackRetryIntervalSeconds; }
    public void   setCallbackRetryIntervalSeconds(int v) { callbackRetryIntervalSeconds = v; }

    public List<String> getCallbackUrls()         { return callbackUrls; }
    public void         setCallbackUrls(List<String> v) { callbackUrls = v; }

    public int    getMinLatencySeconds()          { return minLatencySeconds; }
    public void   setMinLatencySeconds(int v)     { minLatencySeconds = v; }

    public int    getMaxLatencySeconds()          { return maxLatencySeconds; }
    public void   setMaxLatencySeconds(int v)     { maxLatencySeconds = v; }

    public int    getNoResponseHoldSeconds()      { return noResponseHoldSeconds; }
    public void   setNoResponseHoldSeconds(int v) { noResponseHoldSeconds = v; }

    public int    getGetMinLatencySeconds()       { return getMinLatencySeconds; }
    public void   setGetMinLatencySeconds(int v)  { getMinLatencySeconds = v; }

    public int    getGetMaxLatencySeconds()       { return getMaxLatencySeconds; }
    public void   setGetMaxLatencySeconds(int v)  { getMaxLatencySeconds = v; }

    /** Derived: no-response rate = 1 - acceptRate - rejectRate */
    public double getNoResponseRate() {
        return Math.max(0.0, 1.0 - acceptRate - rejectRate);
    }
}
