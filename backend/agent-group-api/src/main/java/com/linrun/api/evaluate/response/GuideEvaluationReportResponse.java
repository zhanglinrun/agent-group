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
    private List<GuideEvaluationItemDTO> items = new ArrayList<>();
    private List<GuideEvaluationFeedbackDTO> feedbacks = new ArrayList<>();
}
