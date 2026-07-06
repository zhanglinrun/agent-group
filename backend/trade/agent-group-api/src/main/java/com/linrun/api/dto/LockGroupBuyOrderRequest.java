package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LockGroupBuyOrderRequest implements Serializable {

    private String userId;
    private String goodsId;
    private String decisionId;
    private String activityId;
    private String teamId;
    private String idempotentKey;
    private String payChannel;
}















