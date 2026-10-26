package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** One payout row in the POST request body. */
public record DisbursementRowRequest(

        @NotBlank(message = "row_id is required")
        @JsonProperty("row_id")
        String rowId,

        @NotBlank(message = "to_wallet is required")
        @JsonProperty("to_wallet")
        String toWallet,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @JsonProperty("amount")
        BigDecimal amount
) {}
