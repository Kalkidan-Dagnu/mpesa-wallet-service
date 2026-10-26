package com.mpesa.walletservice.repository;

import com.mpesa.walletservice.dto.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByPhoneNumber(String phoneNumber);
}
