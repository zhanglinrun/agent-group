package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentGatewayErrorMapResponse implements Serializable {

    private String payChannel;
    private String gatewayCode;
    private String businessCode;
    private String businessMessage;
    private boolean retryable;
    private String suggestion;
}















