package com.mpesa.walletservice.service;

import com.mpesa.walletservice.dto.Wallet;
import com.mpesa.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public Wallet getById(Long id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + id));
    }
}
