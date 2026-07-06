package com.linrun.domain.trade.model.payment;

import java.time.LocalDateTime;

public record PaymentCertificateRefreshResult(
        String payChannel,
        boolean refreshed,
        String certificateSerialNo,
        LocalDateTime refreshTime,
        String message) {
}















