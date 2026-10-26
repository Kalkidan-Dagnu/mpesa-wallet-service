package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for GET /mpesa/v1/b2c/disbursements/{batch_id}.
 * Also returned as the body of the 200 on idempotent POST replay.
 *
 * <pre>
 * {
 *   "batch_id":    "PAYROLL-2026-11",
 *   "status":      "COMPLETED",
 *   "total_rows":  5000,
 *   "succeeded":   4945,
 *   "failed":      55,
 *   "failed_rows": [ { "row_id": "17", "error": "WALLET_NOT_ACTIVE" } ]
 * }
 * </pre>
 */
public record DisbursementBatchResponse(

        @JsonProperty("batch_id")
        String batchId,

        @JsonProperty("status")
        BatchStatus status,

        @JsonProperty("total_rows")
        int totalRows,

        @JsonProperty("succeeded")
        int succeeded,

        @JsonProperty("failed")
        int failed,

        @JsonProperty("failed_rows")
        List<FailedRowResponse> failedRows
) {
    public static DisbursementBatchResponse from(DisbursementBatch batch,
                                                 List<DisbursementRow> rows) {
        List<FailedRowResponse> failed = rows.stream()
                .filter(r -> r.getStatus() == RowStatus.FAILED)
                .map(r -> new FailedRowResponse(r.getRowId(), r.getErrorCode()))
                .toList();

        return new DisbursementBatchResponse(
                batch.getBatchId(),
                batch.getStatus(),
                batch.getTotalRows(),
                batch.getSucceeded(),
                batch.getFailed(),
                failed
        );
    }
}
