package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserModelConfigRequest implements Serializable {

    private Boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
}
