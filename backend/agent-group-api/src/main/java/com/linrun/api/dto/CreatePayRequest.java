package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePayRequest implements Serializable {

    private String userId;
    private String productId;
    private String decisionId;
    private String teamId;
    private Integer marketType;
    private String activityId;
    private String payChannel;
    private String idempotentKey;
}
