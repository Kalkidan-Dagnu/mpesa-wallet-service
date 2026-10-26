package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.EntryResponse;
import com.mpesa.walletservice.dto.StatementResponse;
import com.mpesa.walletservice.dto.WalletEntry;
import com.mpesa.walletservice.repository.WalletEntryRepository;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Statement retrieval with keyset-cursor pagination.
 *
 * ── Algorithm ───────────────────────────────────────────────────────────────
 * 1. Decode cursor → beforeEntryId  (Long.MAX_VALUE on first page)
 * 2. Query for (limit + 1) rows  WHERE wallet_id = ? AND entry_id < beforeEntryId
 *                                 ORDER BY entry_id DESC
 * 3. If result.size() > limit → a next page exists.
 *    next_cursor = encode( result.get(limit).entryId )  — the (limit+1)-th row
 *    Trim result to exactly limit rows before returning.
 * 4. If result.size() ≤ limit → last page.  next_cursor = null.
 *
 * ── Why limit+1 and not COUNT(*) ────────────────────────────────────────────
 * COUNT(*) requires a full index scan over all matching rows.  Fetching one
 * extra row is O(1) extra work and avoids a second round-trip to the DB.
 *
 * ── Snapshot semantics ──────────────────────────────────────────────────────
 * entry_id is a BIGSERIAL that only increases.  Entries written after the
 * first page have entry_id > first-page cursor, so they never appear on
 * subsequent pages.  Following cursors until next_cursor == null returns every
 * entry that existed at first-request time, exactly once.
 *
 * ── Read-only transaction ────────────────────────────────────────────────────
 * @Transactional(readOnly = true) tells Hibernate not to dirty-check entities
 * and lets the connection pool route to a read replica when one is configured.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT     = 200;
    static final int MIN_LIMIT     = 1;

    private final WalletRepository      walletRepository;
    private final WalletEntryRepository entryRepository;
    private final CursorCodec           cursorCodec;

    /**
     * Return one page of statement entries for the given wallet.
     *
     * @param walletId numeric wallet identifier (path variable, no "W-" prefix)
     * @param limit    page size; null → {@value #DEFAULT_LIMIT}
     * @param cursor   opaque cursor from a previous response; null → first page
     * @return statement page with entries and optional next_cursor
     * @throws NoSuchElementException              if the wallet does not exist
     * @throws IllegalArgumentException            if limit is out of range
     * @throws CursorCodec.InvalidCursorException  if cursor cannot be decoded
     */
    @Transactional(readOnly = true)
    public StatementResponse getStatement(long walletId, Integer limit, String cursor) {

        // ── 1. Validate limit ────────────────────────────────────────────────
        int pageSize = (limit == null) ? DEFAULT_LIMIT : limit;
        if (pageSize < MIN_LIMIT || pageSize > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT
                    + ", got " + pageSize);
        }

        // ── 2. Resolve wallet (fail fast with 404 if not found) ──────────────
        if (!walletRepository.existsById(walletId)) {
            throw new NoSuchElementException("Wallet not found: " + walletId);
        }

        // ── 3. Decode cursor ─────────────────────────────────────────────────
        // Long.MAX_VALUE as sentinel means "start from the newest entry".
        // The query is entry_id < beforeEntryId, so MAX_VALUE includes everything.
        long beforeEntryId = (cursor == null || cursor.isBlank())
                ? Long.MAX_VALUE
                : cursorCodec.decode(cursor);   // throws InvalidCursorException on bad input

        // ── 4. Keyset query — fetch one extra row to detect next page ─────────
        List<WalletEntry> fetched = entryRepository.findPageBefore(
                walletId, beforeEntryId, pageSize + 1);

        // ── 5. Determine next cursor ─────────────────────────────────────────
        String nextCursor;
        List<WalletEntry> page;

        if (fetched.size() > pageSize) {
            // The (pageSize+1)-th row exists — there is more data.
            // Its entry_id becomes the cursor: "next page starts before this id".
            long nextBefore = fetched.get(pageSize).getEntryId();
            nextCursor = cursorCodec.encode(nextBefore);
            page = fetched.subList(0, pageSize);
        } else {
            nextCursor = null;
            page = fetched;
        }

        // ── 6. Map to response ───────────────────────────────────────────────
        List<EntryResponse> entries = page.stream()
                .map(EntryResponse::from)
                .toList();

        return new StatementResponse("W" + walletId, entries, nextCursor);
    }
}
