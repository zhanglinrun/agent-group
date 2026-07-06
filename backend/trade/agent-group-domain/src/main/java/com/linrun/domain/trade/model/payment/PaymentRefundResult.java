package com.linrun.domain.trade.model.payment;

public class PaymentRefundResult {

    private String orderId;
    private String payOrderId;
    private String refundId;
    private boolean success;
    private String message;

    public static PaymentRefundResult success(String orderId, String payOrderId, String refundId, String message) {
        PaymentRefundResult result = new PaymentRefundResult();
        result.setOrderId(orderId);
        result.setPayOrderId(payOrderId);
        result.setRefundId(refundId);
        result.setSuccess(true);
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

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}















