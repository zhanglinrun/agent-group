package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class WeixinTemplateMessageRequest implements Serializable {

    private String userId;
    private String openId;
    private String templateId;
    private String targetUrl;
    private String title;
    private String remark;
    private Map<String, String> data = new HashMap<>();
}















