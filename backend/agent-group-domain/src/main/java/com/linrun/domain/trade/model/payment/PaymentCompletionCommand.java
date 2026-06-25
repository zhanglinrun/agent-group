package com.linrun.domain.trade.model.payment;

import java.time.LocalDateTime;

public class PaymentCompletionCommand {

    private String orderId;
    private String gatewayTradeNo;
    private LocalDateTime payTime;

    public static PaymentCompletionCommand paid(String orderId, String gatewayTradeNo, LocalDateTime payTime) {
        PaymentCompletionCommand command = new PaymentCompletionCommand();
        command.setOrderId(orderId);
        command.setGatewayTradeNo(gatewayTradeNo);
        command.setPayTime(payTime);
        return command;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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
