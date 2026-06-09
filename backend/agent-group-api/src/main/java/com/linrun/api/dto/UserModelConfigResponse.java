package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserModelConfigResponse implements Serializable {

    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String textBaseUrl;
    private String textModel;
    private String imageBaseUrl;
    private String imageModel;
    private String keyMasked;
    private String textKeyMasked;
    private String imageKeyMasked;
    private LocalDateTime updateTime;
}















