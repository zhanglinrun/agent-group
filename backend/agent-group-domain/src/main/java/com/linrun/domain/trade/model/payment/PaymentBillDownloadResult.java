package com.linrun.domain.trade.model.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentBillDownloadResult(
        String payChannel,
        LocalDate billDate,
        String billType,
        String downloadUrl,
        boolean downloaded,
        boolean parsed,
        int totalCount,
        BigDecimal totalAmount,
        String summary,
        String message) {
}
