package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WeixinTemplateMessageResponse implements Serializable {

    private boolean success;
    private String mode;
    private String messageId;
    private String openId;
    private String payload;
    private String message;
}
