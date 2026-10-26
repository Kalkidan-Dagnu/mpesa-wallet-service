package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /mpesa/v1/b2c/disbursements request body.
 *
 * <pre>
 * {
 *   "batch_id":      "PAYROLL-2026-11",
 *   "source_wallet": "M001",
 *   "rows": [
 *     { "row_id": "1", "to_wallet": "W002", "amount": 1000 }
 *   ]
 * }
 * </pre>
 */
public record DisbursementRequest(

        @NotBlank(message = "batch_id is required")
        @JsonProperty("batch_id")
        String batchId,

        @NotBlank(message = "source_wallet is required")
        @JsonProperty("source_wallet")
        String sourceWallet,

        @NotEmpty(message = "rows must not be empty")
        @Size(max = 5000, message = "rows must not exceed 5000")
        @Valid
        @JsonProperty("rows")
        List<DisbursementRowRequest> rows
) {}
