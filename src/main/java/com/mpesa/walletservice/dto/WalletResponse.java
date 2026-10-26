package com.mpesa.walletservice.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
public class WalletResponse {

    private Long id;
    private String phoneNumber;
    private String currency;
    private BigDecimal balance;
    private WalletStatus status;
    private BigDecimal reserved;
    private BigDecimal available;

}
