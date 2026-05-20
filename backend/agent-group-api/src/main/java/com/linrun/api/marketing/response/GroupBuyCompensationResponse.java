package com.linrun.api.marketing.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GroupBuyCompensationResponse implements Serializable {

    private String orderId;
    private String payOrderId;
    private String refundId;
    private String teamId;
    private String orderStatus;
    private String payStatus;
    private String lockStatus;
    private String teamStatus;
    private Integer lockedCount;
    private Integer completeCount;
    private BigDecimal refundAmount;
    private LocalDateTime finishTime;
}
