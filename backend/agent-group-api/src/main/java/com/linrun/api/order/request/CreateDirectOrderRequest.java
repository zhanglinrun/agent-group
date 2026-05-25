package com.linrun.api.order.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateDirectOrderRequest implements Serializable {

    private String userId;
    private String goodsId;
    private String decisionId;
    private String idempotentKey;
    private String payChannel;
}
