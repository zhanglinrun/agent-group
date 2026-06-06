package com.linrun.domain.account.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserMembershipAccount {

    private String userId;
    private String planCode;
    private String planName;
    private String status;
    private BigDecimal monthlyQuota = BigDecimal.ZERO;
    private BigDecimal monthlyUsedQuota = BigDecimal.ZERO;
    private LocalDateTime cycleStartTime;
    private LocalDateTime cycleEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isActive(LocalDateTime now) {
        return "ACTIVE".equalsIgnoreCase(status)
                && cycleStartTime != null
                && cycleEndTime != null
                && !now.isBefore(cycleStartTime)
                && now.isBefore(cycleEndTime);
    }

    public BigDecimal remainingQuota() {
        BigDecimal remaining = monthlyQuota.subtract(monthlyUsedQuota);
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getMonthlyQuota() {
        return monthlyQuota;
    }

    public void setMonthlyQuota(BigDecimal monthlyQuota) {
        this.monthlyQuota = monthlyQuota == null ? BigDecimal.ZERO : monthlyQuota;
    }

    public BigDecimal getMonthlyUsedQuota() {
        return monthlyUsedQuota;
    }

    public void setMonthlyUsedQuota(BigDecimal monthlyUsedQuota) {
        this.monthlyUsedQuota = monthlyUsedQuota == null ? BigDecimal.ZERO : monthlyUsedQuota;
    }

    public LocalDateTime getCycleStartTime() {
        return cycleStartTime;
    }

    public void setCycleStartTime(LocalDateTime cycleStartTime) {
        this.cycleStartTime = cycleStartTime;
    }

    public LocalDateTime getCycleEndTime() {
        return cycleEndTime;
    }

    public void setCycleEndTime(LocalDateTime cycleEndTime) {
        this.cycleEndTime = cycleEndTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
