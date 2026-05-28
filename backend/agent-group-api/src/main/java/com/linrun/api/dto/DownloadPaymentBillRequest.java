package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class DownloadPaymentBillRequest implements Serializable {

    private String payChannel;
    private LocalDate billDate;
    private String billType;
    private boolean downloadContent;
    private String billFileUrl;
}
