package com.mpesa.walletservice.dto;

/**
 * Immutable set of ledger entry types.
 *
 * Rules:
 *  - TRANSFER_IN / DEPOSIT      → amount is positive  → balance_after increases
 *  - TRANSFER_OUT / WITHDRAWAL / FEE → amount is negative → balance_after decreases
 *  - REVERSAL                   → amount sign mirrors the entry being reversed
 *
 * Reservations are NOT entries; a payout becomes an entry only when it succeeds.
 */
public enum EntryType {
    TRANSFER_IN,
    TRANSFER_OUT,
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    REVERSAL
}
