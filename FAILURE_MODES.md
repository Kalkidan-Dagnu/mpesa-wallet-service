# FAILURE_MODES.md

## API: POST /v1/payments/charge

### Potential Failure Modes
* **Lose money:** User is charged but the transaction state is not saved locally, resulting in a completed upstream payment with no internal record. [Prevented via: Idempotent transactional outbox pattern]
* **Lose money:** Database transaction times out after the payment gateway succeeds, rolling back the ledger entry while the cash left the user's account. [Prevented via: Two-phase commit with reconciling background workers]
* **Create money:** Refund API triggers multiple times due to rapid user clicks, returning more cash than the original transaction value. [Prevented via: Strict database constraint checking `amount_refunded <= original_amount`]
* **Create money:** Decimal rounding errors in multi-currency conversion accumulate fractional currency units over millions of API calls. [Prevented via: Using arbitrary-precision integers (cents/minor units) for all calculations]
* **Pay twice:** Network timeout occurs after the payment request reaches the gateway but before the response returns, leading to a manual retry by the client. [Prevented via: Enforcing mandatory client-generated `idempotency_key`]
* **Pay twice:** Race condition allows concurrent identical API requests to pass the balance check simultaneously before the first deduction commits. [Prevented via: Database row-level locking (`SELECT FOR UPDATE`) on user balance]
* **Block a thread:** Heavy cryptographic verification of payloads happens synchronously on the main HTTP event loop thread. [Prevented via: Offloading crypto routines to a dedicated worker thread pool]
* **Block a thread:** Database connection pool runs out of available connections during peak traffic, forcing incoming API requests to wait synchronously. [Prevented via: Configuring non-blocking connection acquisition timeouts and circuit breakers]
* **Hang:** Third-party payment gateway experience a silent degradation, keeping TCP connections open indefinitely without sending data. [Prevented via: Implementing strict 5000ms HTTP connect and read timeouts]
* **Hang:** Distributed lock for a specific user ID is acquired but never released because the server instance crashed mid-execution. [Prevented via: Setting a mandatory Time-To-Live (TTL) expiration on all distributed locks]
