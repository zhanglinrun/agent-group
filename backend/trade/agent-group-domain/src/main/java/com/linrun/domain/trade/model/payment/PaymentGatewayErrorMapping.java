package com.linrun.domain.trade.model.payment;

public record PaymentGatewayErrorMapping(
        String payChannel,
        String gatewayCode,
        String businessCode,
        String businessMessage,
        boolean retryable,
        String suggestion) {
}















