package com.mpesa.walletservice.controller;

import com.mpesa.walletservice.dto.TransferRequest;
import com.mpesa.walletservice.dto.TransferResponse;
import com.mpesa.walletservice.repository.TransferRepository;
import com.mpesa.walletservice.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /mpesa/v1/p2p/transfers
 */
@RestController
@RequestMapping("/mpesa/v1/p2p/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService    transferService;
    private final TransferRepository transferRepository;

    /**
     * Move money from one wallet to another.
     *
     * <p>Requires an {@code Idempotency-Key} header. The same key from any
     * instance always returns the same {@code transfer_id} and never moves
     * money twice.
     *
     * <p>Returns 201 Created on first execution; 200 OK on idempotent replay.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key")  String          idempotencyKey,
            @Valid @RequestBody                TransferRequest request
    ) {
        try {
            TransferResponse response = transferService.transfer(idempotencyKey, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (TransferService.IdempotencyRaceException e) {
            // Another instance won the race and committed the same key.
            // The current transaction has been rolled back. Re-read the winner.
            TransferResponse response = transferRepository
                    .findByIdempotencyKey(e.idempotencyKey)
                    .map(TransferResponse::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "Race loser could not find winning transfer for key: "
                            + e.idempotencyKey));
            // Replay → 200, not 201
            return ResponseEntity.ok(response);
        }
    }
}
