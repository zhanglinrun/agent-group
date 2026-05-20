package com.linrun.api.order.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class TradeStatusFlowDTO implements Serializable {

    private String flowId;
    private String orderId;
    private String bizType;
    private String bizId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String remark;
    private LocalDateTime createTime;
}
