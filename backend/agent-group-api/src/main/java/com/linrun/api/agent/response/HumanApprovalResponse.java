package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HumanApprovalResponse implements Serializable {

    private String approvalId;
    private String userId;
    private String action;
    private String bizId;
    private String summary;
    private String riskLevel;
    private BigDecimal amount;
    private String status;
    private String reason;
    private LocalDateTime expireTime;
    private String message;
}
