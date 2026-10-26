package com.mpesa.walletservice.controller;

import com.mpesa.walletservice.dto.StatementResponse;
import com.mpesa.walletservice.dto.WalletResponse;
import com.mpesa.walletservice.service.StatementService;
import com.mpesa.walletservice.service.WalletService;
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

    private final StatementService statementService;
    private final WalletService walletService;



    /**
     * GET /mpesa/v1/wallets/{id}/statement
     */
    @GetMapping("/{id}/statement")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable("id")                          long    walletId,
            @RequestParam(value = "limit",  required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String  cursor
    ) {
        StatementResponse response = statementService.getStatement(walletId, limit, cursor);
        return ResponseEntity.ok(response);
    }


}
