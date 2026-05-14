package com.linrun.api.payment.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PaymentWebhookResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String orderStatus;
    private String payStatus;
    private String gatewayTradeNo;
    private LocalDateTime payTime;
    private boolean verified;
    private String message;
}
