package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response body for POST /mpesa/v1/p2p/transfers (201 Created).
 *
 * <pre>
 * {
 *   "transfer_id":          "T-10293",
 *   "status":               "COMPLETED",
 *   "from_available_after": 990000,
 *   "created_at":           "2026-11-02T09:14:03.120Z"
 * }
 * </pre>
 */
public record TransferResponse(

        @JsonProperty("transfer_id")
        String transferId,

        @JsonProperty("status")
        String status,

        @JsonProperty("from_available_after")
        BigDecimal fromAvailableAfter,

        @JsonProperty("created_at")
        Instant createdAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                "T-" + t.getTransferId(),
                "COMPLETED",
                t.getFromAvailableAfter(),
                t.getCreatedAt()
        );
    }
}
