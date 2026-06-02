package com.linrun.infrastructure.po;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserQuotaAccountPO {

    private String userId;
    private BigDecimal quotaBalance;
    private BigDecimal frozenQuota;
    private BigDecimal usedQuota;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getQuotaBalance() {
        return quotaBalance;
    }

    public void setQuotaBalance(BigDecimal quotaBalance) {
        this.quotaBalance = quotaBalance;
    }

    public BigDecimal getFrozenQuota() {
        return frozenQuota;
    }

    public void setFrozenQuota(BigDecimal frozenQuota) {
        this.frozenQuota = frozenQuota;
    }

    public BigDecimal getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(BigDecimal usedQuota) {
        this.usedQuota = usedQuota;
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
