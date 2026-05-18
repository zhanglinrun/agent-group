package com.linrun.api.evaluate.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class GuideEvaluationReportResponse implements Serializable {

    private String batchNo;
    private String promptVersion;
    private String knowledgeVersion;
    private Integer totalCount;
    private BigDecimal retrievalHitRate;
    private BigDecimal answerAccuracyRate;
    private BigDecimal recommendationReasonableRate;
    private BigDecimal contextConsistencyRate;
    private Long averageLatencyMillis;
    private Long p99LatencyMillis;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private BigDecimal estimatedCostYuan;
    private String baselineBatchNo;
    private BigDecimal retrievalHitRateDelta;
    private BigDecimal answerAccuracyRateDelta;
    private BigDecimal recommendationReasonableRateDelta;
    private BigDecimal contextConsistencyRateDelta;
    private List<GuideEvaluationItemDTO> items = new ArrayList<>();
    private List<GuideEvaluationFeedbackDTO> feedbacks = new ArrayList<>();
}
