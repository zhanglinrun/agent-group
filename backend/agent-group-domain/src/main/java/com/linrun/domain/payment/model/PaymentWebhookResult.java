package com.linrun.domain.payment.model;

import java.time.LocalDateTime;

public class PaymentWebhookResult {

    private String orderId;
    private String payOrderId;
    private String gatewayTradeNo;
    private LocalDateTime payTime;
    private boolean verified;
    private String message;

    public static PaymentWebhookResult verified(String orderId, String payOrderId, String gatewayTradeNo,
                                                LocalDateTime payTime, String message) {
        PaymentWebhookResult result = new PaymentWebhookResult();
        result.setOrderId(orderId);
        result.setPayOrderId(payOrderId);
        result.setGatewayTradeNo(gatewayTradeNo);
        result.setPayTime(payTime);
        result.setVerified(true);
        result.setMessage(message);
        return result;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPayOrderId() {
        return payOrderId;
    }

    public void setPayOrderId(String payOrderId) {
        this.payOrderId = payOrderId;
    }

    public String getGatewayTradeNo() {
        return gatewayTradeNo;
    }

    public void setGatewayTradeNo(String gatewayTradeNo) {
        this.gatewayTradeNo = gatewayTradeNo;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
