package com.linrun.domain.quota.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserQuotaFlow {

    private String flowId;
    private String userId;
    private String flowType;
    private String bizId;
    private BigDecimal quotaAmount = BigDecimal.ZERO;
    private BigDecimal beforeBalance = BigDecimal.ZERO;
    private BigDecimal afterBalance = BigDecimal.ZERO;
    private String remark;
    private LocalDateTime createTime;

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public BigDecimal getQuotaAmount() {
        return quotaAmount;
    }

    public void setQuotaAmount(BigDecimal quotaAmount) {
        this.quotaAmount = quotaAmount == null ? BigDecimal.ZERO : quotaAmount;
    }

    public BigDecimal getBeforeBalance() {
        return beforeBalance;
    }

    public void setBeforeBalance(BigDecimal beforeBalance) {
        this.beforeBalance = beforeBalance == null ? BigDecimal.ZERO : beforeBalance;
    }

    public BigDecimal getAfterBalance() {
        return afterBalance;
    }

    public void setAfterBalance(BigDecimal afterBalance) {
        this.afterBalance = afterBalance == null ? BigDecimal.ZERO : afterBalance;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}















