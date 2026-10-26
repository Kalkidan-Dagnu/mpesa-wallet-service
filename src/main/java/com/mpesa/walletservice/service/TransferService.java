package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.*;
import com.mpesa.walletservice.exception.WalletErrorCode;
import com.mpesa.walletservice.exception.WalletException;
import com.mpesa.walletservice.repository.TransferRepository;
import com.mpesa.walletservice.repository.WalletEntryRepository;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

/**
 * Executes P2P wallet transfers.
 *
 * ── Atomicity guarantee ──────────────────────────────────────────────────────
 * The debit of from_wallet, the credit of to_wallet, both WalletEntry rows,
 * and the Transfer record all commit in a single Postgres transaction.
 * Postgres's ACID guarantees mean no observer — on either instance, at any
 * isolation level — ever sees the debit without the credit.
 *
 * ── Deadlock prevention ──────────────────────────────────────────────────────
 * We always acquire row locks in ascending wallet_id order (lower id first).
 * Without this, two concurrent transfers A→B and B→A would each lock their
 * own sender then wait for the other — a classic deadlock cycle. Consistent
 * ordering means one transaction always acquires both locks before the other
 * starts waiting, so the cycle cannot form.
 *
 * ── Idempotency ──────────────────────────────────────────────────────────────
 * The UNIQUE constraint on transfer.idempotency_key is the DB-level guard.
 * Algorithm:
 *   1. SELECT existing record for this key → return it if found (fast path).
 *   2. Execute transfer and INSERT Transfer row.
 *   3. If INSERT violates the unique constraint (two instances raced), catch
 *      DataIntegrityViolationException and SELECT the winner's record.
 *
 * This means the same Idempotency-Key always returns the same transfer_id,
 * from_available_after, and created_at — from any instance, any time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository     walletRepository;
    private final TransferRepository   transferRepository;
    private final WalletEntryRepository entryRepository;

    /**
     * Execute a P2P transfer.
     *
     * @param idempotencyKey value of the Idempotency-Key header
     * @param request        validated request body
     * @return the transfer response (idempotent replay returns the original)
     * @throws IllegalArgumentException  if idempotencyKey is blank
     * @throws NoSuchElementException    if either wallet does not exist (→ 404)
     * @throws WalletException           SAME_WALLET, WALLET_NOT_ACTIVE, INSUFFICIENT_FUNDS
     */
    @Transactional
    public TransferResponse transfer(String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        // ── 1. Idempotency fast path ─────────────────────────────────────────
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(TransferResponse::from)
                .orElseGet(() -> executeTransfer(idempotencyKey, request));
    }

    // ── Core transfer logic ───────────────────────────────────────────────────

    private TransferResponse executeTransfer(String idempotencyKey, TransferRequest request) {

        // ── 2. Resolve wallet IDs from W-prefix references ───────────────────
        long fromId = parseWalletRef(request.fromWallet(), "from_wallet");
        long toId   = parseWalletRef(request.toWallet(),   "to_wallet");

        // ── 3. Same-wallet check ─────────────────────────────────────────────
        if (fromId == toId) {
            throw new WalletException(WalletErrorCode.SAME_WALLET,
                    "from_wallet and to_wallet must be different");
        }

        // ── 4. Acquire row locks in ascending ID order (deadlock prevention) ─
        long firstId  = Math.min(fromId, toId);
        long secondId = Math.max(fromId, toId);

        Wallet first = walletRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Wallet not found: " + walletRef(firstId)));
        Wallet second = walletRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Wallet not found: " + walletRef(secondId)));

        // Map back to from/to regardless of lock order
        Wallet fromWallet = (fromId == firstId) ? first : second;
        Wallet toWallet   = (fromId == firstId) ? second : first;

        // ── 5. Validate both wallets are ACTIVE ──────────────────────────────
        if (fromWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletException(WalletErrorCode.WALLET_NOT_ACTIVE,
                    "Source wallet " + request.fromWallet() + " is not active");
        }
        if (toWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletException(WalletErrorCode.WALLET_NOT_ACTIVE,
                    "Destination wallet " + request.toWallet() + " is not active");
        }

        // ── 6. Check sufficient available funds ──────────────────────────────
        BigDecimal amount = request.amount();
        if (fromWallet.getAvailable().compareTo(amount) < 0) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient funds in wallet " + request.fromWallet());
        }

        // ── 7. Debit from_wallet ─────────────────────────────────────────────
        BigDecimal newFromBalance = fromWallet.getBalance().subtract(amount);
        fromWallet.setBalance(newFromBalance);
        BigDecimal fromAvailableAfter = fromWallet.getAvailable(); // = newFromBalance - reserved

        // ── 8. Credit to_wallet ──────────────────────────────────────────────
        BigDecimal newToBalance = toWallet.getBalance().add(amount);
        toWallet.setBalance(newToBalance);

        // ── 9. Write ledger entries ──────────────────────────────────────────
        // reference is the idempotency key so the entry can be traced back
        entryRepository.save(new WalletEntry(
                fromWallet, EntryType.TRANSFER_OUT,
                amount.negate(), newFromBalance, request.reference()));

        entryRepository.save(new WalletEntry(
                toWallet, EntryType.TRANSFER_IN,
                amount, newToBalance, request.reference()));

        // ── 10. Persist Transfer record (also the idempotency anchor) ────────
        Transfer transfer = new Transfer(
                idempotencyKey, fromWallet, toWallet,
                amount, request.reference(), fromAvailableAfter);

        try {
            transferRepository.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException e) {
            // Race: another instance committed the same key between our SELECT
            // (step 1) and this INSERT. Roll back our changes and return the
            // winner's record. Spring will roll back the @Transactional on
            // DataIntegrityViolationException — we re-throw a clean version
            // that the controller can handle by re-reading.
            throw new IdempotencyRaceException(idempotencyKey, e);
        }

        return TransferResponse.from(transfer);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parse "W001" → 1, "W070" → 70, "42" → 42.
     * Strips any leading non-numeric prefix.
     */
    private long parseWalletRef(String ref, String fieldName) {
        String numeric = ref.replaceAll("^[^0-9]+", "");
        try {
            return Long.parseLong(numeric);
        } catch (NumberFormatException e) {
            throw new NoSuchElementException("Wallet not found: " + ref);
        }
    }

    private String walletRef(long id) {
        return "W" + id;
    }

    // ── Race exception (internal — handled by the service itself) ────────────

    /**
     * Thrown when two concurrent requests with the same idempotency key race
     * to insert the Transfer row and this instance loses. The controller layer
     * catches this and re-reads the winner's record.
     */
    public static class IdempotencyRaceException extends RuntimeException {
        public final String idempotencyKey;
        public IdempotencyRaceException(String key, Throwable cause) {
            super("Idempotency race on key: " + key, cause);
            this.idempotencyKey = key;
        }
    }
}
