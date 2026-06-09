package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BillingPolicyDTO implements Serializable {

    private BigDecimal platformPromptCostPer1k;
    private BigDecimal platformCompletionCostPer1k;
    private BigDecimal customModelServiceRate;
    private boolean memberCustomModelFree;
    private String unit;
}















