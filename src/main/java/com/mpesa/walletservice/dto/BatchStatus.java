package com.mpesa.walletservice.dto;

public enum BatchStatus {
    /** Accepted; rows not yet started. */
    PENDING,
    /** Worker is actively processing rows. */
    PROCESSING,
    /** All rows have been attempted (some may have failed). */
    COMPLETED
}
