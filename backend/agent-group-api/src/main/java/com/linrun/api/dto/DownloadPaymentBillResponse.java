package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DownloadPaymentBillResponse implements Serializable {

    private String payChannel;
    private LocalDate billDate;
    private String billType;
    private String downloadUrl;
    private boolean downloaded;
    private boolean parsed;
    private int totalCount;
    private BigDecimal totalAmount;
    private String summary;
    private String message;
}















