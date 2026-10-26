package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Funds earmarked for in-flight payouts. Written to DB so both instances see it. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reserved = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WalletStatus status = WalletStatus.ACTIVE;

    /** Timestamp of the last status transition. Null until first transition. */
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Last time any field on this row changed. */
    @Column(nullable = false, name = "changed_at")
    private Instant updatedAt = Instant.now();

    /**
     * Optimistic lock version — incremented by Hibernate on every UPDATE.
     * Detects concurrent modifications from any instance without a DB lock.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    // ── Derived field — NOT a column ─────────────────────────────────────────

    /**
     * Spendable balance: balance minus reserved funds.
     *
     * Invariant: available ≥ 0 is enforced at write time (debit operations
     * must check this before decrementing balance).  The getter re-derives it
     * on every call from the two stored columns, so a GET request always
     * returns a consistent snapshot regardless of how the entity was loaded.
     */
    public BigDecimal getAvailable() {
        return balance.subtract(reserved);
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Wallet(String phoneNumber, String currency) {
        this.phoneNumber = phoneNumber;
        this.currency = currency;
    }
}
