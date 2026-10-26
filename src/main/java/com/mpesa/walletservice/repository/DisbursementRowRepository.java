package com.mpesa.walletservice.repository;

import com.mpesa.walletservice.dto.DisbursementRow;
import com.mpesa.walletservice.dto.DisbursementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DisbursementRowRepository extends JpaRepository<DisbursementRow, Long> {

    /**
     * All rows for a batch, ordered by their original position.
     * Used by the GET response to build failed_rows.
     */
    List<DisbursementRow> findAllByBatchOrderByRowOrder(DisbursementBatch batch);

    /**
     * PENDING rows for a batch, ordered for processing.
     * The processor reads a page at a time to bound memory usage.
     *
     * Hits index idx_disbursement_row_batch_status (batch_id, status, row_order).
     */
    @Query(value = """
            SELECT * FROM disbursement_row
             WHERE batch_id  = :batchId
               AND status    = 'PENDING'
             ORDER BY row_order ASC
             LIMIT :limit
            """, nativeQuery = true)
    List<DisbursementRow> findPendingRows(
            @Param("batchId") String batchId,
            @Param("limit")   int    limit);

    /**
     * Atomically increment succeeded or failed counter on the batch row.
     * Called inside the same transaction as the row status update, so both
     * commit together or neither does.
     */
    @Modifying
    @Query("""
            UPDATE DisbursementBatch b
               SET b.succeeded = b.succeeded + :deltaSucceeded,
                   b.failed    = b.failed    + :deltaFailed
             WHERE b.batchId = :batchId
            """)
    void incrementCounters(
            @Param("batchId")        String batchId,
            @Param("deltaSucceeded") int    deltaSucceeded,
            @Param("deltaFailed")    int    deltaFailed);
}
