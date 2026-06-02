package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class GuideUsageMetricsDTO implements Serializable {

    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private BigDecimal estimatedCostYuan;
    private Long llmLatencyMillis;
    private Long totalLatencyMillis;
    private Boolean fallbackUsed;
    private BigDecimal consumedQuota;
    private BigDecimal remainingQuota;
    private String model;
}
