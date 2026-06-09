package com.linrun.domain.account.model;

import java.math.BigDecimal;

public class UserQuotaAccount {

    private String userId;
    private BigDecimal quotaBalance = BigDecimal.ZERO;
    private BigDecimal frozenQuota = BigDecimal.ZERO;
    private BigDecimal usedQuota = BigDecimal.ZERO;

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
        this.quotaBalance = quotaBalance == null ? BigDecimal.ZERO : quotaBalance;
    }

    public BigDecimal getFrozenQuota() {
        return frozenQuota;
    }

    public void setFrozenQuota(BigDecimal frozenQuota) {
        this.frozenQuota = frozenQuota == null ? BigDecimal.ZERO : frozenQuota;
    }

    public BigDecimal getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(BigDecimal usedQuota) {
        this.usedQuota = usedQuota == null ? BigDecimal.ZERO : usedQuota;
    }
}















