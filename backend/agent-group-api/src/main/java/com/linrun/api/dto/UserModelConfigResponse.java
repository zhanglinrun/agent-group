package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserModelConfigResponse implements Serializable {

    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String keyMasked;
    private LocalDateTime updateTime;
}
