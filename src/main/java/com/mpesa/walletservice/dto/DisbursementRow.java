package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One payout row within a {@link DisbursementBatch}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "disbursement_row",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_disbursement_row_batch_row",
                          columnNames = {"batch_id", "row_id"})
    },
    indexes = {
        // Drives the processor's "next PENDING rows for this batch" query
        @Index(name = "idx_disbursement_row_batch_status",
               columnList = "batch_id, status, row_order")
    }
)
public class DisbursementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_disbursement_row_batch"))
    private DisbursementBatch batch;

    /** Caller-supplied identifier, unique within the batch. */
    @Column(name = "row_id", nullable = false, updatable = false, length = 64)
    private String rowId;

    /** Destination wallet identifier string as supplied by the caller. */
    @Column(name = "to_wallet_ref", nullable = false, updatable = false, length = 20)
    private String toWalletRef;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** 1-based position in the original request; drives processing order. */
    @Column(name = "row_order", nullable = false, updatable = false)
    private int rowOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RowStatus status = RowStatus.PENDING;

    /**
     * Set when status = FAILED. Null when PENDING or SUCCEEDED.
     * Values: WALLET_NOT_FOUND, WALLET_NOT_ACTIVE, INSUFFICIENT_FUNDS,
     *         DUPLICATE_ROW.
     */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DisbursementRow(DisbursementBatch batch, String rowId,
                           String toWalletRef, BigDecimal amount, int rowOrder) {
        this.batch = batch;
        this.rowId = rowId;
        this.toWalletRef = toWalletRef;
        this.amount = amount;
        this.rowOrder = rowOrder;
    }

    public void markSucceeded() {
        this.status = RowStatus.SUCCEEDED;
        this.processedAt = Instant.now();
    }

    public void markFailed(String code) {
        this.status = RowStatus.FAILED;
        this.errorCode = code;
        this.processedAt = Instant.now();
    }
}
