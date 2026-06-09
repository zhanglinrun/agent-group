package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WeixinLoginStatusResponse implements Serializable {

    private String sceneId;
    private String status;
    private String userId;
    private String openId;
    private String nickname;
    private LocalDateTime expireTime;
    private LocalDateTime scanTime;
}















