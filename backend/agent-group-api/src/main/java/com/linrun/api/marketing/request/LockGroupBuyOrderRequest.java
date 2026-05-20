package com.linrun.api.marketing.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LockGroupBuyOrderRequest implements Serializable {

    private String userId;
    private String goodsId;
    private String activityId;
    private String teamId;
    private String idempotentKey;
    private String payChannel;
    private String goodsName;
    private BigDecimal originalAmount;
    private BigDecimal payAmount;
}
