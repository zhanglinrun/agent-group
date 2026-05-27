package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CreateHumanApprovalRequest implements Serializable {

    private String userId;
    private String action;
    private String bizId;
    private String summary;
    private String riskLevel;
    private BigDecimal amount;
}
