package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CreatePaymentResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String payChannel;
    private String payUrl;
    private String payFormHtml;
    private String paymentType;
    private String gatewayTradeNo;
    private BigDecimal payAmount;
    private boolean created;
    private String message;
}















