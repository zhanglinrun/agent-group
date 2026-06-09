package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QuotaFlowDTO implements Serializable {

    private String flowId;
    private String userId;
    private String flowType;
    private String bizId;
    private BigDecimal quotaAmount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String remark;
    private LocalDateTime createTime;
}















