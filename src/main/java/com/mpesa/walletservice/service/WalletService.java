package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.*;
import com.mpesa.walletservice.exception.WalletErrorCode;
import com.mpesa.walletservice.exception.WalletException;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    // ── GET /mpesa/v1/wallets/{id} ───────────────────────────────────────────

    /**
     * Return a consistent snapshot of the wallet.
     *
     * @throws NoSuchElementException if no wallet exists with the given id
     */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + id));
        return WalletResponse.from(wallet);
    }

    // ── PUT /mpesa/v1/wallets/{id}/status ────────────────────────────────────

    /**
     * Set the wallet status to FROZEN or ACTIVE.
     *
     * <p><b>Idempotency:</b> if the wallet is already at the requested status,
     * this method returns immediately without writing anything.  The response
     * contains the existing {@code statusChangedAt} timestamp.
     *
     * <p><b>Freeze guarantee:</b> this method uses a pessimistic write lock
     * (SELECT … FOR UPDATE) on the wallet row.  Any concurrent debit that
     * correctly acquires the same lock will either:
     * <ul>
     *   <li>commit before this transaction acquires the lock — it proceeds,
     *       consistent with the spec ("requests already in flight commit
     *       before the freeze takes effect"), or</li>
     *   <li>wait for this lock, then read FROZEN status after this transaction
     *       commits — it throws {@link WalletException}(WALLET_NOT_ACTIVE).</li>
     * </ul>
     * This is a DB-level guarantee and works across multiple JVM instances
     * because the lock is held in Postgres, not in a JVM heap.
     *
     * <p><b>Timeout:</b> Spring's default lock timeout is the DB default
     * (unlimited in Postgres).  Configure
     * {@code spring.jpa.properties.jakarta.persistence.lock.timeout=1800}
     * (ms) in application.properties to cap it at 1.8 s and leave headroom
     * for the response to be returned within the 2 s SLA.
     *
     * @throws NoSuchElementException if no wallet exists with the given id
     */
    @Transactional
    public StatusUpdateResponse setStatus(Long id, StatusUpdateRequest request) {


        Wallet wallet = walletRepository.findByIdWithLock(id)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + id));

        if (wallet.getStatus() == request.status()) {
            return StatusUpdateResponse.from(wallet);
        }

        wallet.setStatus(request.status());
        wallet.setStatusChangedAt(Instant.now());

        return StatusUpdateResponse.from(wallet);
    }

    // ── Shared internal helper ───────────────────────────────────────────────

    /**
     * Assert that a wallet is ACTIVE before a debit operation proceeds.
     *
     * <p>Call this inside a transaction that has already acquired the row lock,
     * after loading the wallet with {@code findByIdWithLock}, so the status
     * check and the subsequent balance mutation are atomic.
     *
     * @throws WalletException (WALLET_NOT_ACTIVE) if the wallet is not ACTIVE
     */
    public void assertActive(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletException(
                    WalletErrorCode.WALLET_NOT_ACTIVE,
                    "Wallet " + wallet.getId() + " is not active"
            );
        }
    }
}
