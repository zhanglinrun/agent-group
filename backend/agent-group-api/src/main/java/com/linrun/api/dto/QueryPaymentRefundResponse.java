package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QueryPaymentRefundResponse implements Serializable {

    private String payChannel;
    private String orderId;
    private String payOrderId;
    private String gatewayTradeNo;
    private String refundId;
    private String refundStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private boolean verified;
    private String rawBody;
    private String message;
}















