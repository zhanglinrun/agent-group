package com.linrun.domain.trade.model.payment;

import java.time.LocalDate;

public record PaymentBillDownloadCommand(
        String payChannel,
        LocalDate billDate,
        String billType,
        boolean downloadContent,
        String billFileUrl) {
}















