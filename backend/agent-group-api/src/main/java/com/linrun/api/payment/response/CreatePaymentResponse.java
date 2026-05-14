package com.linrun.api.payment.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CreatePaymentResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String payChannel;
    private String payUrl;
    private String gatewayTradeNo;
    private BigDecimal payAmount;
    private boolean created;
    private String message;
}
