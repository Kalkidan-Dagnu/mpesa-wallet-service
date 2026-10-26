package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record of a completed P2P transfer.
 *
 * ── Idempotency ──────────────────────────────────────────────────────────────
 * idempotency_key has a UNIQUE constraint. The first INSERT wins; a concurrent
 * INSERT of the same key violates the constraint. The service catches
 * DataIntegrityViolationException and returns the existing record, so the
 * caller always gets the same transfer_id and created_at regardless of how
 * many times it submits the same key.
 *
 * This works across two JVM instances because the constraint lives in Postgres.
 *
 * ── Atomicity ────────────────────────────────────────────────────────────────
 * A Transfer row is only inserted inside the same transaction that debits
 * from_wallet, credits to_wallet, and writes two WalletEntry rows. Either
 * all four writes commit or none do. No observer ever sees the debit without
 * the credit.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "transfer",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_transfer_idempotency_key",
                          columnNames = "idempotency_key")
    }
)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id", updatable = false, nullable = false)
    private Long transferId;

    /** Caller-supplied idempotency key from the Idempotency-Key header. */
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_wallet_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_transfer_from_wallet"))
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_wallet_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_transfer_to_wallet"))
    private Wallet toWallet;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 64)
    private String reference;

    /** Balance of from_wallet immediately after this transfer committed. */
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal fromAvailableAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Transfer(String idempotencyKey, Wallet fromWallet, Wallet toWallet,
                    BigDecimal amount, String reference, BigDecimal fromAvailableAfter) {
        this.idempotencyKey    = idempotencyKey;
        this.fromWallet        = fromWallet;
        this.toWallet          = toWallet;
        this.amount            = amount;
        this.reference         = reference;
        this.fromAvailableAfter = fromAvailableAfter;
        this.createdAt         = Instant.now();
    }
}
