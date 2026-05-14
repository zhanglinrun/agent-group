package com.linrun.api.payment.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReconcilePaymentResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String payChannel;
    private String localOrderStatus;
    private String localPayStatus;
    private String gatewayTradeNo;
    private BigDecimal localPayAmount;
    private LocalDate billDate;
    private boolean matched;
    private String message;
}
