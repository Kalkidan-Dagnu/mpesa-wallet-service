package com.mpesa.walletservice.repository;

import com.mpesa.walletservice.dto.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {

    @Query(value = """
            SELECT *
              FROM wallet_entry
             WHERE wallet_id   = :walletId
               AND entry_id    < :beforeEntryId
             ORDER BY entry_id DESC
             LIMIT :limit
            """,
           nativeQuery = true)
    List<WalletEntry> findPageBefore(
            @Param("walletId")      long   walletId,
            @Param("beforeEntryId") long   beforeEntryId,
            @Param("limit")         int    limit
    );
}
