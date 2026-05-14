package com.linrun.api.payment.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundPaymentRequest implements Serializable {

    private String orderId;
    private String refundReason;
}
