package com.mpesa.walletservice.exception;

import com.mpesa.walletservice.service.CursorCodec;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Translates service-layer exceptions into RFC 9457 ProblemDetail responses.
 *
 * Status mapping:
 *   NoSuchElementException           → 404 Not Found
 *   IllegalArgumentException         → 400 Bad Request
 *   InvalidCursorException           → 400 Bad Request
 *   MethodArgumentNotValidException  → 400 Bad Request  (@Valid failures)
 *   WalletException                  → 422 Unprocessable Entity
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Wallet Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request Parameter");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(CursorCodec.InvalidCursorException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCursor(CursorCodec.InvalidCursorException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Cursor");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * @Valid constraint violations on request bodies (e.g. missing required fields).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Business-rule violations: wallet not active, insufficient balance, etc.
     * Returns 422 with a machine-readable {@code error_code} field.
     */
    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ProblemDetail> handleWalletException(WalletException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Wallet Operation Failed");
        problem.setProperty("error_code", ex.getErrorCode().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * Same batch_id resubmitted with different rows → 409 Conflict.
     */
    @ExceptionHandler(DisbursementConflictException.class)
    public ResponseEntity<ProblemDetail> handleDisbursementConflict(DisbursementConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Batch Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Pessimistic lock timed out (another transaction held the row lock longer
     * than {@code jakarta.persistence.lock.timeout}).  The caller should retry.
     * Returns 503 Service Unavailable with a Retry-After hint.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockTimeout(PessimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The wallet is under heavy contention. Retry the request.");
        problem.setTitle("Lock Timeout");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(problem);
    }
}
