package com.linrun.domain.trade.model.payment;

public class PaymentCreateResult {

    private String orderId;
    private String payOrderId;
    private String payChannel;
    private String payUrl;
    private String payFormHtml;
    private String paymentType;
    private String gatewayTradeNo;
    private boolean created;
    private String message;

    public static PaymentCreateResult created(String orderId, String payOrderId, String payChannel,
                                              String payUrl, String gatewayTradeNo, String message) {
        PaymentCreateResult result = new PaymentCreateResult();
        result.setOrderId(orderId);
        result.setPayOrderId(payOrderId);
        result.setPayChannel(payChannel);
        result.setPayUrl(payUrl);
        result.setPaymentType("URL");
        result.setGatewayTradeNo(gatewayTradeNo);
        result.setCreated(true);
        result.setMessage(message);
        return result;
    }

    public static PaymentCreateResult pageForm(String orderId, String payOrderId, String payChannel,
                                               String payFormHtml, String gatewayTradeNo, String message) {
        PaymentCreateResult result = created(orderId, payOrderId, payChannel, payFormHtml, gatewayTradeNo, message);
        result.setPayFormHtml(payFormHtml);
        result.setPaymentType("PAGE_FORM");
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

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getPayFormHtml() {
        return payFormHtml;
    }

    public void setPayFormHtml(String payFormHtml) {
        this.payFormHtml = payFormHtml;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getGatewayTradeNo() {
        return gatewayTradeNo;
    }

    public void setGatewayTradeNo(String gatewayTradeNo) {
        this.gatewayTradeNo = gatewayTradeNo;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
