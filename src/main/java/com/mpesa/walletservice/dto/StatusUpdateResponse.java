package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response body for PUT /mpesa/v1/wallets/{id}/status.
 */
public record StatusUpdateResponse(

        @JsonProperty("wallet_id")
        String walletId,

        @JsonProperty("status")
        WalletStatus status,

        @JsonProperty("changed_at")
        Instant changedAt
) {
    public static StatusUpdateResponse from(Wallet w) {
        return new StatusUpdateResponse(
                "W" + w.getId(),
                w.getStatus(),
                w.getStatusChangedAt()
        );
    }
}
