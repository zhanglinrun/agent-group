package com.linrun.domain.payment.model;

public class PaymentReconcileResult {

    private String orderId;
    private String payOrderId;
    private String gatewayTradeNo;
    private boolean matched;
    private String message;

    public static PaymentReconcileResult matched(String orderId, String payOrderId, String gatewayTradeNo,
                                                 String message) {
        PaymentReconcileResult result = new PaymentReconcileResult();
        result.setOrderId(orderId);
        result.setPayOrderId(payOrderId);
        result.setGatewayTradeNo(gatewayTradeNo);
        result.setMatched(true);
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

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
