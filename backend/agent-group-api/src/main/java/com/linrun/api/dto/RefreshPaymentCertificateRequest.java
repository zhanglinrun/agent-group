package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefreshPaymentCertificateRequest implements Serializable {

    private String payChannel;
}
