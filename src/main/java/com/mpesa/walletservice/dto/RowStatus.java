package com.mpesa.walletservice.dto;

public enum RowStatus {
    /** Not yet attempted by the processor. */
    PENDING,
    /** Transfer completed successfully. */
    SUCCEEDED,
    /** Transfer failed; see error_code for reason. */
    FAILED
}
