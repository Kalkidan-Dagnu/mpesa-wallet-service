package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable ledger line for a wallet.
 *
 * ── Accounting invariant ────────────────────────────────────────────────────
 * For any two consecutive entries, newer e and older f:
 *     f.balance_after + e.amount == e.balance_after
 *
 * amount is negative for debits (TRANSFER_OUT, WITHDRAWAL, FEE) and positive
 * for credits (TRANSFER_IN, DEPOSIT).  balance_after is always non-negative.
 *
 * ── Pagination design ───────────────────────────────────────────────────────
 * entry_id is a BIGSERIAL (monotonically increasing, never reused, never
 * updated).  The statement query is:
 *
 *     SELECT * FROM wallet_entry
 *     WHERE wallet_id = :walletId
 *       AND entry_id < :cursor          -- keyset — skip already-seen rows
 *     ORDER BY entry_id DESC
 *     LIMIT :limit
 *
 * The composite index on (wallet_id, entry_id DESC) makes this an index-range
 * scan regardless of how many total entries exist.  At 1 M rows, the index
 * size for this table is roughly 50 MB; the query touches only the top-N
 * leaf pages, keeping latency well under 150 ms.
 *
 * ── Snapshot semantics ──────────────────────────────────────────────────────
 * entry_id only increases.  A cursor of "start from entry_id < X" is stable:
 * new entries written after the first page (entry_id > first_page_max) never
 * appear on subsequent pages, so following next_cursor until null returns
 * every entry that existed at first-page time, exactly once.
 *
 * ── Multi-instance note ─────────────────────────────────────────────────────
 * Entries are immutable after insert.  No in-JVM cache is needed or safe.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "wallet_entry",
    indexes = {
        @Index(name = "idx_wallet_entry_wallet_id_entry_id", columnList = "wallet_id, entry_id DESC")
    }
)
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id", updatable = false, nullable = false)
    private Long entryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", updatable = false, nullable = false,
                foreignKey = @ForeignKey(name = "fk_wallet_entry_wallet"))
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private EntryType type;


    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;


    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;


    @Column(nullable = false, updatable = false, length = 64)
    private String reference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WalletEntry(Wallet wallet, EntryType type, BigDecimal amount,
                       BigDecimal balanceAfter, String reference) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reference = reference;
        this.createdAt = Instant.now();
    }
}
