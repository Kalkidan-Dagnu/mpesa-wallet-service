package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single ledger line as returned by the statement API.
 *
 * JSON shape (spec):
 * <pre>
 * {
 *   "entry_id":      "E-88120",
 *   "type":          "TRANSFER_OUT",
 *   "amount":        -1000,
 *   "balance_after": 998000,
 *   "reference":     "T-10293",
 *   "created_at":    "2026-11-02T09:20:01.330Z"
 * }
 * </pre>
 *
 * Accounting invariant enforced at write time (not here):
 *     previous.balance_after + this.amount == this.balance_after
 */
public record EntryResponse(

        /** Prefixed with "E-" to match the spec shape, e.g. "E-88120". */
        @JsonProperty("entry_id")
        String entryId,

        @JsonProperty("type")
        EntryType type,

        /**
         * Signed amount. Negative = debit, positive = credit.
         * The spec example shows -1000 for TRANSFER_OUT.
         */
        @JsonProperty("amount")
        BigDecimal amount,

        /** Running wallet balance immediately after this entry was applied. */
        @JsonProperty("balance_after")
        BigDecimal balanceAfter,

        @JsonProperty("reference")
        String reference,

        @JsonProperty("created_at")
        Instant createdAt
) {
    /** Build from the JPA entity. */
    public static EntryResponse from(WalletEntry e) {
        return new EntryResponse(
                "E-" + e.getEntryId(),
                e.getType(),
                e.getAmount(),
                e.getBalanceAfter(),
                e.getReference(),
                e.getCreatedAt()
        );
    }
}
