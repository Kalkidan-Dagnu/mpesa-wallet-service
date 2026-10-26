package com.mpesa.walletservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One failed row in the GET /disbursements/{batch_id} response. */
public record FailedRowResponse(

        @JsonProperty("row_id")
        String rowId,

        @JsonProperty("error")
        String error
) {}
