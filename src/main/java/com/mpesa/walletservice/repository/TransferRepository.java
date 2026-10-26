package com.mpesa.walletservice.repository;

import com.mpesa.walletservice.dto.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /**
     * Look up a completed transfer by its idempotency key.
     * Used to return the original result on replay without re-executing.
     */
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
