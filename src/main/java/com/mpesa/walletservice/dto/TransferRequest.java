package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request body for POST /mpesa/v1/p2p/transfers.
 *
 * <pre>
 * {
 *   "from_wallet": "W001",
 *   "to_wallet":   "W002",
 *   "amount":      10000,
 *   "reference":   "rent-october"
 * }
 * </pre>
 */
public record TransferRequest(

        @NotBlank(message = "from_wallet is required")
        @JsonProperty("from_wallet")
        String fromWallet,

        @NotBlank(message = "to_wallet is required")
        @JsonProperty("to_wallet")
        String toWallet,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "1", message = "amount must be at least 1")
        @DecimalMax(value = "5000000", message = "amount must not exceed 5,000,000")
        @JsonProperty("amount")
        BigDecimal amount,

        @NotBlank(message = "reference is required")
        @Size(min = 1, max = 64, message = "reference must be 1 to 64 characters")
        @JsonProperty("reference")
        String reference
) {}
