package com.mpesa.walletservice.exception;

/** Thrown when the same batch_id is resubmitted with a different set of rows. */
public class DisbursementConflictException extends RuntimeException {
    public DisbursementConflictException(String message) {
        super(message);
    }
}
