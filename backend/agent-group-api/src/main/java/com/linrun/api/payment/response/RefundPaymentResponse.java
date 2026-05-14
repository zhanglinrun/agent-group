package com.linrun.api.payment.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundPaymentResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String refundId;
    private String orderStatus;
    private String payStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private String message;
}
