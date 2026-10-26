package com.mpesa.walletservice.controller;

import com.mpesa.walletservice.dto.DisbursementBatchResponse;
import com.mpesa.walletservice.dto.DisbursementRequest;
import com.mpesa.walletservice.service.DisbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * B2C disbursement endpoints.
 *
 * POST  /mpesa/v1/b2c/disbursements             → 202 (new) or 200 (replay)
 * GET   /mpesa/v1/b2c/disbursements/{batch_id}  → 200 with current state
 */
@RestController
@RequestMapping("/mpesa/v1/b2c/disbursements")
@RequiredArgsConstructor
public class DisbursementController {

    private final DisbursementService disbursementService;

    /**
     * Submit a disbursement batch.
     *
     * <p>Returns 202 on the first accepted submission. The batch is processed
     * asynchronously; poll GET to track progress.
     *
     * <p>Returns 200 if the same batch_id is resubmitted with identical rows
     * (idempotent replay). The response body contains the current batch state.
     *
     * <p>Returns 409 if the same batch_id is resubmitted with different rows.
     */
    @PostMapping
    public ResponseEntity<DisbursementBatchResponse> submit(
            @Valid @RequestBody DisbursementRequest request
    ) {
        DisbursementService.SubmitResult result = disbursementService.submitBatch(request);
        DisbursementBatchResponse body = disbursementService.getBatch(result.batch().getBatchId());

        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Get the current state of a disbursement batch.
     *
     * <p>Can be polled until {@code status} is {@code COMPLETED}.
     */
    @GetMapping("/{batch_id}")
    public ResponseEntity<DisbursementBatchResponse> getBatch(
            @PathVariable("batch_id") String batchId
    ) {
        return ResponseEntity.ok(disbursementService.getBatch(batchId));
    }
}
