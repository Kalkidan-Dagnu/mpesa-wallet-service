# DESIGN.md

---

## Q1 — Every Wait

**HikariCP connection pool.**  
hikari.connection-timeout=2000 ms. Each instance holds 10 connections; Tomcat has 50
threads. From the 11th concurrent request, threads block up to 2 000 ms waiting for a
connection to be returned, then fail with SQLTransientConnectionException. The
disbursement executor (4 threads) shares the same pool; 4 active batch rows leave only 6
connections for HTTP threads.

**Pessimistic row lock — SELECT … FOR UPDATE.**  
Three sites: WalletService.setStatus, TransferService.executeTransfer (two wallet
rows, acquired in ascending ID order), DisbursementProcessor.executeTransfer (source
wallet). Timeout: jakarta.persistence.lock.timeout=1800 ms. A thread that cannot
acquire the lock within 1 800 ms receives PessimisticLockingFailureException → 503 with
Retry-After: 1.

awaitTerminationSeconds=65. On JVM shutdown, the context waits up to 65 s for in-flight
batch threads to complete before forcing termination.

The wallet-service never calls the bank directly. Bank latency (2–6 s normal, 60 s
no-response) lives entirely on the fake bank's own threads.


## Q2 — PostgreSQL Restart During a Payout

Assume a payout row exists with status=PENDING and wallet.reserved has been
incremented, but the bank has not yet sent its callback.

**Restart happens.** Postgres replays WAL and rolls back any uncommitted transactions.
The reservation commit was already durable, so reserved survives intact. No balance has
moved.

**JVM restarts.** HikariCP detects broken connections on next borrow and re-establishes
them automatically. 

**Bank retries the callback.** The fake bank retries POST /mpesa/v1/callbacks/bank every
3s up to 5 times. The callback handler opens a fresh transaction, loads the payout row
by payout_id, and on SUCCEEDED: debits balance, decrements reserved, inserts a
ledger entry. On FAILED: decrements reserved only.

**If the callback never arrives.** reserved stays elevated permanently. A reconciliation
job (not yet built) would poll GET /bank/payouts/{id} for payouts older than a
configurable TTL and resolve them. Without it, the wallet's available balance is
understated but no money is lost or duplicated.

---

## Q3 — What Was Left Out

**Callback receiver — POST /mpesa/v1/callbacks/bank.**  
Not implemented. Build: a @RestController that enforces idempotency via
UNIQUE(event_id) on a PayoutCallback table — duplicate callbacks (30% arrive 2–3
times) silently discard after the first INSERT. On SUCCEEDED: one transaction debits
balance, decrements reserved, inserts a WALLET_OUT ledger entry. On FAILED:
decrements reserved only.  
**Hard part:** the callback can arrive before POST /bank/payouts returns (delay ≥1 s,
bank response ≥2 s). The payout row must be persisted with status=PENDING before the
bank call, so the callback handler always has a row to update.

**End-to-end tests.**  
Not implemented. Build with @SpringBootTest against both instances (8081, 8082) and the
fake bank (9090). Key scenarios: normal payout settles correctly; duplicate callbacks
debit exactly once (compare GET /bank/total-paid against ledger); freeze during
in-flight transfer returns 422; concurrent A→B and B→A transfers produce correct final
balances with no deadlock.  
**Hard part:** the 60 s no-response scenario makes the suite slow. Mitigation: override
BANK_NO_RESPONSE_HOLD_SECONDS=2 in the test environment.

**Reconciliation job.**  
Not implemented. A @Scheduled task would query payouts stuck in PENDING beyond a TTL,
call GET /bank/payouts/{id}, and apply the result.

---

## Decisions Left Open by the Spec

| Decision | Choice made |
|---|---|
| Wallet ID format (M001, W002) | Strip leading non-numeric prefix, parse remainder as Long |
| Cross-currency transfers | Not validated; transfers across different currency values are permitted |
| Schema migration tool | ddl-auto=update for dev; comment in code notes Flyway/Liquibase for production |

## Isolation Level

All transactions run at Postgres's default **READ COMMITTED**. Balance-mutating reads
are immediately followed by SELECT FOR UPDATE, which re-reads the row under an
exclusive lock, ensuring the balance and reserved values used for arithmetic are the
latest committed state regardless of intervening commits.

## Idempotency Mechanism

**P2P transfer** — UNIQUE(idempotency_key) on transfer. Service reads before writing;
on concurrent race, DataIntegrityViolationException rolls back the losing transaction
and the controller re-reads the winner's row.

**Disbursement batch** — batch_id is the PRIMARY KEY of disbursement_batch. First
saveAndFlush wins; DataIntegrityViolationException causes the loser to read back the
winner. Per-row: UNIQUE(batch_id, row_id) on disbursement_row; processor checks
status=PENDING before acting, so a crash-restart never re-processes a SUCCEEDED row.

**Wallet status change** — SELECT FOR UPDATE serialises concurrent requests; early
return if status already matches makes it a no-op.
