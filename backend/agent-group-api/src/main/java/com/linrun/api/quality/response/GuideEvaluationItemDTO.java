package com.linrun.api.quality.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class GuideEvaluationItemDTO implements Serializable {

    private String caseId;
    private String caseName;
    private String question;
    private String expectedGoodsId;
    private String actualGoodsId;
    private Boolean referencePassed;
    private Boolean answerPassed;
    private Boolean recommendationPassed;
    private Boolean contextPassed;
    private String actualToolNames;
    private Boolean toolCallPassed;
    private Boolean toolArgumentPassed;
    private Boolean toolResultReferencePassed;
    private Long latencyMillis;
    private Long llmLatencyMillis;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private BigDecimal estimatedCostYuan;
    private Boolean fallbackUsed;
    private Integer score;
    private String suggestion;
}
