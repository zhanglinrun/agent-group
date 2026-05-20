package com.linrun.api.marketing.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LockGroupBuyOrderResponse implements Serializable {

    private String lockId;
    private String userId;
    private String goodsId;
    private String activityId;
    private String teamId;
    private Integer teamSize;
    private Integer lockedCount;
    private Integer remainingCount;
    private String teamStatus;
    private String lockStatus;
    private BigDecimal lockAmount;
    private LocalDateTime lockTime;
    private boolean repeated;
    private String orderId;
    private String payOrderId;
    private String orderStatus;
    private String payStatus;
    private String payUrl;
}
