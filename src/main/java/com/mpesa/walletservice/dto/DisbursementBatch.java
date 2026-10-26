package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One merchant-initiated payout batch.
 *
 * ── Idempotency ─────────────────────────────────────────────────────────────
 * batch_id is the caller-supplied primary key (not a generated surrogate).
 * The first INSERT wins; a concurrent INSERT of the same batch_id violates
 * the PRIMARY KEY constraint, which the service layer catches to detect a
 * replay and returns the existing record instead of processing again.
 *
 * "Same batch_id with different rows" → 409 Conflict.
 * "Same batch_id with same rows"      → 200 with current state (idempotent).
 *
 * ── Multi-instance note ─────────────────────────────────────────────────────
 * Two instances may receive the same POST simultaneously. The PK constraint
 * ensures exactly one INSERT succeeds at the DB level. The other instance
 * catches the constraint violation and reads the winning record.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "disbursement_batch")
public class DisbursementBatch {

    /** Caller-supplied idempotency key, e.g. "PAYROLL-2026-11". */
    @Id
    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    /**
     * Wallet that funds all rows. Loaded lazily; only needed by the processor.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_wallet_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_batch_source_wallet"))
    private Wallet sourceWallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BatchStatus status = BatchStatus.PENDING;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int succeeded = 0;

    @Column(nullable = false)
    private int failed = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public DisbursementBatch(String batchId, Wallet sourceWallet, int totalRows) {
        this.batchId = batchId;
        this.sourceWallet = sourceWallet;
        this.totalRows = totalRows;
    }
}
