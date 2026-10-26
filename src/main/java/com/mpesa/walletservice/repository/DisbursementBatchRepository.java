package com.mpesa.walletservice.repository;

import com.mpesa.walletservice.dto.DisbursementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementBatchRepository extends JpaRepository<DisbursementBatch, String> {
    // PK is batch_id (String); findById(batchId) is sufficient for all lookups.
}
