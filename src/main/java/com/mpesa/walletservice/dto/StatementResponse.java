package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level response for GET /mpesa/v1/wallets/{id}/statement.
 *
 * JSON shape (spec):
 * <pre>
 * {
 *   "wallet_id":   "W070",
 *   "entries":     [ { ... } ],
 *   "next_cursor": "opaque-string-or-null"
 * }
 * </pre>
 *
 * Pagination contract:
 *  - next_cursor is null  → this is the last page; no more entries exist
 *                           (as of the moment the first page was requested).
 *  - next_cursor non-null → pass it as the {@code cursor} query param to
 *                           retrieve the next page.
 *  - Following cursors from the first page until next_cursor is null returns
 *    every entry that existed at first-request time, exactly once, even while
 *    new entries are concurrently being written.
 */
public record StatementResponse(

        /** Prefixed with "W-" to match the spec shape, e.g. "W070". */
        @JsonProperty("wallet_id")
        String walletId,

        @JsonProperty("entries")
        List<EntryResponse> entries,

        /**
         * Opaque Base64url cursor pointing to the next page.
         * Null when there are no more entries.
         */
        @JsonProperty("next_cursor")
        String nextCursor
) {}
