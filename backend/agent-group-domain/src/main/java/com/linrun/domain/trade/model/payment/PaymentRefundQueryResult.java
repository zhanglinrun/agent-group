package com.linrun.domain.trade.model.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRefundQueryResult(
        String payChannel,
        String orderId,
        String payOrderId,
        String gatewayTradeNo,
        String refundId,
        String refundStatus,
        BigDecimal refundAmount,
        LocalDateTime refundTime,
        boolean verified,
        String rawBody,
        String message) {
}
