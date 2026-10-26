package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.*;
import com.mpesa.walletservice.exception.DisbursementConflictException;
import com.mpesa.walletservice.repository.DisbursementBatchRepository;
import com.mpesa.walletservice.repository.DisbursementRowRepository;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Handles POST (submit) and GET (status) for disbursement batches.
 *
 * ── Idempotency design ───────────────────────────────────────────────────────
 * batch_id is the PRIMARY KEY of disbursement_batch. The first INSERT wins.
 * If a second POST arrives with the same batch_id:
 *
 *   Case A — identical rows → return current state (200, idempotent replay).
 *   Case B — different rows → 409 Conflict.
 *
 * This works across two JVM instances because the constraint lives in Postgres,
 * not in a JVM heap. Both instances can race; only one INSERT succeeds.
 *
 * ── What "same rows" means ───────────────────────────────────────────────────
 * Same count AND same set of row_ids (order-insensitive). We do not re-validate
 * amounts or destinations on replay — the canonical copy is what was stored.
 *
 * ── Source wallet ────────────────────────────────────────────────────────────
 * The source wallet is resolved by its string reference. The spec uses "M001"
 * style IDs. We look up by wallet ID (numeric part after stripping leading
 * letters). If no such wallet exists, the whole batch is rejected with 422.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementService {

    private final DisbursementBatchRepository batchRepository;
    private final DisbursementRowRepository   rowRepository;
    private final WalletRepository            walletRepository;
    private final DisbursementProcessor       processor;

    // ── POST /mpesa/v1/b2c/disbursements ─────────────────────────────────────

    /**
     * Accept a new batch or replay an existing one.
     *
     * @return the persisted batch (caller maps to 202 for new, 200 for replay)
     * @throws DisbursementConflictException if batch_id exists with different rows
     * @throws NoSuchElementException        if source_wallet not found
     */
    @Transactional
    public SubmitResult submitBatch(DisbursementRequest request) {

        // ── 1. Check for existing batch ──────────────────────────────────────
        Optional<DisbursementBatch> existing = batchRepository.findById(request.batchId());
        if (existing.isPresent()) {
            return handleReplay(existing.get(), request);
        }

        // ── 2. Resolve source wallet ─────────────────────────────────────────
        Wallet sourceWallet = resolveWallet(request.sourceWallet());

        // ── 3. Persist batch header ──────────────────────────────────────────
        DisbursementBatch batch = new DisbursementBatch(
                request.batchId(), sourceWallet, request.rows().size());
        try {
            batchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            // Another instance inserted the same batch_id between our findById
            // and our insert (tiny race window). Read the winner and replay.
            DisbursementBatch winner = batchRepository.findById(request.batchId())
                    .orElseThrow(() -> new IllegalStateException(
                            "batch insert failed but batch not found: " + request.batchId(), e));
            return handleReplay(winner, request);
        }

        // ── 4. Persist rows, marking intra-batch duplicates immediately ──────
        Set<String> seen = new LinkedHashSet<>();
        List<DisbursementRow> rows = new ArrayList<>(request.rows().size());
        int order = 1;
        for (DisbursementRowRequest rr : request.rows()) {
            DisbursementRow row = new DisbursementRow(
                    batch, rr.rowId(), rr.toWallet(), rr.amount(), order++);
            if (!seen.add(rr.rowId())) {
                // Duplicate within this request — mark failed immediately,
                // never attempt this row.
                row.markFailed("DUPLICATE_ROW");
            }
            rows.add(row);
        }
        rowRepository.saveAll(rows);

        // ── 5. Kick off async processing ─────────────────────────────────────
        processor.processAsync(batch.getBatchId());

        return new SubmitResult(batch, false);
    }

    // ── GET /mpesa/v1/b2c/disbursements/{batch_id} ───────────────────────────

    @Transactional(readOnly = true)
    public DisbursementBatchResponse getBatch(String batchId) {
        DisbursementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        List<DisbursementRow> rows = rowRepository.findAllByBatchOrderByRowOrder(batch);
        return DisbursementBatchResponse.from(batch, rows);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private SubmitResult handleReplay(DisbursementBatch existing, DisbursementRequest request) {
        List<DisbursementRow> rows = rowRepository.findAllByBatchOrderByRowOrder(existing);

        // Build the canonical row_id set from what was stored
        Set<String> storedIds = new LinkedHashSet<>();
        rows.forEach(r -> storedIds.add(r.getRowId()));

        // Build the incoming row_id set using record accessor (deduplicated in order)
        Set<String> incomingIds = new LinkedHashSet<>();
        request.rows().forEach(r -> incomingIds.add(r.rowId()));   // record accessor

        if (!storedIds.equals(incomingIds)) {
            throw new DisbursementConflictException(
                    "batch_id '" + existing.getBatchId() +
                    "' already exists with different rows");
        }

        return new SubmitResult(existing, true);  // true = replay
    }

    private Wallet resolveWallet(String walletRef) {
        // Strip leading alpha prefix ("M001" → 1, "W002" → 2, "30" → 30)
        String numeric = walletRef.replaceAll("^[^0-9]+", "");
        try {
            long id = Long.parseLong(numeric);
            return walletRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Source wallet not found: " + walletRef));
        } catch (NumberFormatException e) {
            throw new NoSuchElementException("Source wallet not found: " + walletRef);
        }
    }

    // ── Result carrier ───────────────────────────────────────────────────────

    /** Carries the batch and a flag indicating whether this was an idempotent replay. */
    public record SubmitResult(DisbursementBatch batch, boolean replay) {}
}
