package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RefreshPaymentCertificateResponse implements Serializable {

    private String payChannel;
    private boolean refreshed;
    private String certificateSerialNo;
    private LocalDateTime refreshTime;
    private String message;
}















