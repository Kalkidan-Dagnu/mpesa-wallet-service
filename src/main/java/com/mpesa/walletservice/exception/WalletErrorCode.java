package com.mpesa.walletservice.exception;

/**
 * Machine-readable error codes for 422 Unprocessable Entity responses
 * and per-row disbursement failures.
 */
public enum WalletErrorCode {

    /** A debit was attempted on a wallet whose status is not ACTIVE. */
    WALLET_NOT_ACTIVE,

    /** No wallet exists with the given identifier. */
    WALLET_NOT_FOUND,

    /**
     * from_wallet and to_wallet resolve to the same wallet.
     * A transfer to yourself is not permitted.
     */
    SAME_WALLET,

    /** The debit would make available (balance − reserved) negative. */
    INSUFFICIENT_FUNDS,

    /** A reservation would make available negative. */
    INSUFFICIENT_AVAILABLE_BALANCE,

    /**
     * A row_id appears more than once in the same batch request.
     * The first occurrence is processed; subsequent ones are recorded as
     * FAILED with this code and are never attempted.
     */
    DUPLICATE_ROW,
}
