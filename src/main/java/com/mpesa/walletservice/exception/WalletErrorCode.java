package com.mpesa.walletservice.exception;


public enum WalletErrorCode {

    WALLET_NOT_ACTIVE,

    /**
     * The requested debit would make available (balance − reserved) negative.
     */
    INSUFFICIENT_FUNDS,

    /**
     * The requested reservation would make available negative.
     */
    INSUFFICIENT_AVAILABLE_BALANCE,
}
