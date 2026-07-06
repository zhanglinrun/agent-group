package com.linrun.domain.trade.model.payment;

import java.time.LocalDate;

public class PaymentReconcileCommand {

    private String orderId;
    private String payOrderId;
    private String payChannel;
    private String gatewayTradeNo;
    private LocalDate billDate;

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

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getGatewayTradeNo() {
        return gatewayTradeNo;
    }

    public void setGatewayTradeNo(String gatewayTradeNo) {
        this.gatewayTradeNo = gatewayTradeNo;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }
}















