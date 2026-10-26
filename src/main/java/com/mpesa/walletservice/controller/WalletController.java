package com.mpesa.walletservice.controller;

import com.mpesa.walletservice.dto.*;
import com.mpesa.walletservice.service.StatementService;
import com.mpesa.walletservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for wallet operations.
 *
 * Base path: /mpesa/v1/wallets
 */
@RestController
@RequestMapping("/mpesa/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService    walletService;
    private final StatementService statementService;

    // ── GET /mpesa/v1/wallets/{id} ───────────────────────────────────────────

    /**
     * Returns a consistent snapshot of the wallet.
     *
     */
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable("id") long id) {
        return ResponseEntity.ok(walletService.getWallet(id));
    }

    // ── PUT /mpesa/v1/wallets/{id}/status ────────────────────────────────────

    /**
     * Sets the wallet status to FROZEN or ACTIVE.
     *
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<StatusUpdateResponse> setStatus(
            @PathVariable("id") long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(walletService.setStatus(id, request));
    }

    // ── GET /mpesa/v1/wallets/{id}/statement ─────────────────────────────────

    /**
     * Returns a page of ledger entries for the wallet, newest first.
     * See StatementService for pagination details.
     */
    @GetMapping("/{id}/statement")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable("id")                                long    walletId,
            @RequestParam(value = "limit",  required = false)  Integer limit,
            @RequestParam(value = "cursor", required = false)  String  cursor
    ) {
        return ResponseEntity.ok(statementService.getStatement(walletId, limit, cursor));
    }
}
