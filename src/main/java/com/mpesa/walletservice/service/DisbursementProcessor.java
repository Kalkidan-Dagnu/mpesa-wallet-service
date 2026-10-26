package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.*;
import com.mpesa.walletservice.exception.WalletErrorCode;
import com.mpesa.walletservice.repository.DisbursementBatchRepository;
import com.mpesa.walletservice.repository.DisbursementRowRepository;
import com.mpesa.walletservice.repository.WalletEntryRepository;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Async worker that processes a disbursement batch row by row.
 */
@Component
@RequiredArgsConstructor
public class DisbursementProcessor {

    private static final int PAGE_SIZE = 50;  // rows per processing mini-batch

    private final DisbursementBatchRepository batchRepository;
    private final DisbursementRowRepository rowRepository;
    private final WalletRepository            walletRepository;
    private final WalletEntryRepository       entryRepository;

    // ── Async entry point ────────────────────────────────────────────────────

    /**
     * Called by {@link DisbursementService} after persisting the batch.
     * Runs in a thread from the async executor (not the HTTP thread).
     * The POST response has already been sent by the time this method runs.
     */
    @Async("disbursementExecutor")
    public void processAsync(String batchId) {
        try {
            processBatch(batchId);
        } catch (Exception e) {
            // Batch remains in PROCESSING; a restart will resume from PENDING rows.
        }
    }

    // ── Batch-level orchestration (non-transactional — each row owns its TX) ─

    private void processBatch(String batchId) {
        markBatchProcessing(batchId);

        while (true) {
            List<DisbursementRow> page = rowRepository.findPendingRows(batchId, PAGE_SIZE);
            if (page.isEmpty()) break;

            for (DisbursementRow row : page) {
                processRowInOwnTransaction(batchId, row.getId());
            }
        }

        markBatchCompleted(batchId);
    }

    // ── Per-row transaction ──────────────────────────────────────────────────

    /**
     * Process one row in its own transaction so a failure here does not affect
     * other rows. REQUIRES_NEW suspends any outer transaction (there is none
     * here, but the annotation is explicit for safety).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRowInOwnTransaction(String batchId, Long rowId) {
        DisbursementRow row = rowRepository.findById(rowId).orElse(null);
        if (row == null || row.getStatus() != RowStatus.PENDING) {
            return;  // already handled (crash-recovery idempotency)
        }

        String errorCode = null;
        try {
            executeTransfer(row);
        } catch (RowProcessingException e) {
            errorCode = e.getCode();
        } catch (Exception e) {
            errorCode = "INTERNAL_ERROR";
        }

        if (errorCode == null) {
            row.markSucceeded();
            rowRepository.incrementCounters(batchId, 1, 0);
        } else {
            row.markFailed(errorCode);
            rowRepository.incrementCounters(batchId, 0, 1);
        }
    }

    // ── The actual money movement ────────────────────────────────────────────

    private void executeTransfer(DisbursementRow row) {
        String toRef = row.getToWalletRef();

        // ── Resolve destination wallet ───────────────────────────────────────
        Optional<Wallet> destOpt = resolveWallet(toRef);
        if (destOpt.isEmpty()) {
            throw new RowProcessingException(WalletErrorCode.WALLET_NOT_FOUND.name());
        }
        Wallet dest = destOpt.get();

        BigDecimal amount = row.getAmount();
        String batchId   = row.getBatch().getBatchId();
        String reference  = batchId + "/" + row.getRowId();

        // ── Lock source wallet (SELECT FOR UPDATE) ───────────────────────────
        // Lock order: always source before destination to prevent deadlocks.
        DisbursementBatch batch = row.getBatch();
        Long sourceId = batch.getSourceWallet().getId();
        Wallet source = walletRepository.findByIdWithLock(sourceId)
                .orElseThrow(() -> new RowProcessingException("SOURCE_WALLET_NOT_FOUND"));

        // ── Check source is active ───────────────────────────────────────────
        if (source.getStatus().name().equals("FROZEN")) {
            throw new RowProcessingException(WalletErrorCode.WALLET_NOT_ACTIVE.name());
        }

        // ── Check destination is active ──────────────────────────────────────
        if (dest.getStatus().name().equals("FROZEN")) {
            throw new RowProcessingException(WalletErrorCode.WALLET_NOT_ACTIVE.name());
        }

        // ── Check sufficient funds ───────────────────────────────────────────
        if (source.getAvailable().compareTo(amount) < 0) {
            throw new RowProcessingException(WalletErrorCode.INSUFFICIENT_FUNDS.name());
        }

        // ── Debit source ─────────────────────────────────────────────────────
        BigDecimal newSourceBalance = source.getBalance().subtract(amount);
        source.setBalance(newSourceBalance);
        WalletEntry debitEntry = new WalletEntry(
                source, EntryType.TRANSFER_OUT,
                amount.negate(), newSourceBalance, reference);

        // ── Credit destination ────────────────────────────────────────────────
        BigDecimal newDestBalance = dest.getBalance().add(amount);
        dest.setBalance(newDestBalance);
        WalletEntry creditEntry = new WalletEntry(
                dest, EntryType.TRANSFER_IN,
                amount, newDestBalance, reference);

        entryRepository.save(debitEntry);
        entryRepository.save(creditEntry);
        // Wallet entities are dirty-tracked by Hibernate; no explicit save needed.
    }

    // ── Batch status transitions ─────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBatchProcessing(String batchId) {
        batchRepository.findById(batchId).ifPresent(b -> {
            if (b.getStatus() == BatchStatus.PENDING) {
                b.setStatus(BatchStatus.PROCESSING);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBatchCompleted(String batchId) {
        batchRepository.findById(batchId).ifPresent(b -> b.setStatus(BatchStatus.COMPLETED));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<Wallet> resolveWallet(String ref) {
        String numeric = ref.replaceAll("^[^0-9]+", "");
        try {
            return walletRepository.findById(Long.parseLong(numeric));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ── Checked-like exception for row-level errors ──────────────────────────

    static class RowProcessingException extends RuntimeException {
        private final String code;
        RowProcessingException(String code) {
            super(code);
            this.code = code;
        }
        String getCode() { return code; }
    }
}
