package com.mpesa.walletservice.exception;

/**
 * Thrown when a wallet operation is syntactically valid but cannot be
 * completed due to the wallet's business state (status, balance, etc.).
 * <p>Example response:
 * <pre>
 * HTTP/1.1 422 Unprocessable Entity
 * {
 *   "title":      "Wallet Operation Failed",
 *   "status":     422,
 *   "detail":     "Wallet 30 is not active",
 *   "error_code": "WALLET_NOT_ACTIVE"
 * }
 * </pre>
 */
public class WalletException extends RuntimeException {

    private final WalletErrorCode errorCode;

    public WalletException(WalletErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WalletErrorCode getErrorCode() {
        return errorCode;
    }
}
