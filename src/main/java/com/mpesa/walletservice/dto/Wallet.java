package com.mpesa.walletservice.dto;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import jakarta.validation.constraints.Min;

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

    /** M-Pesa phone number that owns this wallet*/
    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, name = "changed_at")
    private Instant updatedAt = Instant.now();

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    @Formula("balance - reserved")
    @Min(0)
    private BigDecimal available = BigDecimal.ZERO;


    @Version
    @Column(nullable = false)
    private Long version;

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Wallet(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.currency = "SANTIM";
    }
}
