package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class QuotaAccountResponse implements Serializable {

    private String userId;
    private BigDecimal quotaBalance;
    private BigDecimal frozenQuota;
    private BigDecimal usedQuota;
}
