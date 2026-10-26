package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record WalletResponse(

        @JsonProperty("wallet_id")
        String walletId,

        @JsonProperty("status")
        WalletStatus status,

        @JsonProperty("balance")
        BigDecimal balance,

        @JsonProperty("reserved")
        BigDecimal reserved,

        /** balance − reserved.  Never negative. */
        @JsonProperty("available")
        BigDecimal available
) {
    /** Map from entity. available is computed here, not fetched from a column. */
    public static WalletResponse from(Wallet w) {
        return new WalletResponse(
                "W" + w.getId(),
                w.getStatus(),
                w.getBalance(),
                w.getReserved(),
                w.getAvailable()   // getAvailable() = balance - reserved
        );
    }
}
