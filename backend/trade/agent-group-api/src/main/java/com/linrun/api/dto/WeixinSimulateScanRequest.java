package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WeixinSimulateScanRequest implements Serializable {

    private String sceneId;
    private String userId;
    private String openId;
    private String nickname;
}















