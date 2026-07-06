package com.linrun.domain.trade.model.payment;

public record PaymentRefundQueryCommand(
        String payChannel,
        String orderId,
        String payOrderId,
        String gatewayTradeNo,
        String refundId,
        String requestBody,
        java.util.Map<String, String> headers) {
}















