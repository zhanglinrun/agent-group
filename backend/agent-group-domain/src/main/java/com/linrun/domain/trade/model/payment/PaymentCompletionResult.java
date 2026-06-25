package com.linrun.domain.trade.model.payment;

import java.time.LocalDateTime;

public class PaymentCompletionResult {

    private String orderId;
    private String payOrderId;
    private String orderStatus;
    private String payStatus;
    private String gatewayTradeNo;
    private LocalDateTime payTime;

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

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
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
}
