package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WeixinLoginQrRequest implements Serializable {

    private String userId;
    private String redirectUrl;
}
