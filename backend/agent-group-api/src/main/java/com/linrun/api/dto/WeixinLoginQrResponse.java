package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WeixinLoginQrResponse implements Serializable {

    private String sceneId;
    private String ticket;
    private String qrCodeUrl;
    private String localScanUrl;
    private Integer expireSeconds;
    private LocalDateTime expireTime;
    private String status;
    private boolean officialConfigured;
}
