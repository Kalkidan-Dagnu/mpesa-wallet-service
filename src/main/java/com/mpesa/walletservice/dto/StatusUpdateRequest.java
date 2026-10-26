package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /mpesa/v1/wallets/{id}/status.
 */
public record StatusUpdateRequest(

        @NotNull(message = "status is required")
        @JsonProperty("status")
        WalletStatus status,

        @JsonProperty("reason")
        String reason
) {}
