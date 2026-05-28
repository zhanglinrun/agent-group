package com.linrun.infrastructure.po;

import java.math.BigDecimal;

public class GuideEvaluationReportPO {

    private String batchNo;
    private String promptVersion;
    private String knowledgeVersion;
    private int totalCount;
    private BigDecimal retrievalHitRate;
    private BigDecimal answerAccuracyRate;
    private BigDecimal recommendationReasonableRate;
    private BigDecimal contextConsistencyRate;
    private BigDecimal toolCallAccuracyRate;
    private BigDecimal toolArgumentAccuracyRate;
    private BigDecimal toolResultReferenceRate;
    private long averageLatencyMillis;
    private long p99LatencyMillis;
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private BigDecimal estimatedCostYuan;
    private String baselineBatchNo;
    private BigDecimal retrievalHitRateDelta;
    private BigDecimal answerAccuracyRateDelta;
    private BigDecimal recommendationReasonableRateDelta;
    private BigDecimal contextConsistencyRateDelta;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public BigDecimal getRetrievalHitRate() {
        return retrievalHitRate;
    }

    public void setRetrievalHitRate(BigDecimal retrievalHitRate) {
        this.retrievalHitRate = retrievalHitRate;
    }

    public BigDecimal getAnswerAccuracyRate() {
        return answerAccuracyRate;
    }

    public void setAnswerAccuracyRate(BigDecimal answerAccuracyRate) {
        this.answerAccuracyRate = answerAccuracyRate;
    }

    public BigDecimal getRecommendationReasonableRate() {
        return recommendationReasonableRate;
    }

    public void setRecommendationReasonableRate(BigDecimal recommendationReasonableRate) {
        this.recommendationReasonableRate = recommendationReasonableRate;
    }

    public BigDecimal getContextConsistencyRate() {
        return contextConsistencyRate;
    }

    public void setContextConsistencyRate(BigDecimal contextConsistencyRate) {
        this.contextConsistencyRate = contextConsistencyRate;
    }

    public BigDecimal getToolCallAccuracyRate() {
        return toolCallAccuracyRate;
    }

    public void setToolCallAccuracyRate(BigDecimal toolCallAccuracyRate) {
        this.toolCallAccuracyRate = toolCallAccuracyRate;
    }

    public BigDecimal getToolArgumentAccuracyRate() {
        return toolArgumentAccuracyRate;
    }

    public void setToolArgumentAccuracyRate(BigDecimal toolArgumentAccuracyRate) {
        this.toolArgumentAccuracyRate = toolArgumentAccuracyRate;
    }

    public BigDecimal getToolResultReferenceRate() {
        return toolResultReferenceRate;
    }

    public void setToolResultReferenceRate(BigDecimal toolResultReferenceRate) {
        this.toolResultReferenceRate = toolResultReferenceRate;
    }

    public long getAverageLatencyMillis() {
        return averageLatencyMillis;
    }

    public void setAverageLatencyMillis(long averageLatencyMillis) {
        this.averageLatencyMillis = averageLatencyMillis;
    }

    public long getP99LatencyMillis() {
        return p99LatencyMillis;
    }

    public void setP99LatencyMillis(long p99LatencyMillis) {
        this.p99LatencyMillis = p99LatencyMillis;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(long totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getEstimatedCostYuan() {
        return estimatedCostYuan;
    }

    public void setEstimatedCostYuan(BigDecimal estimatedCostYuan) {
        this.estimatedCostYuan = estimatedCostYuan;
    }

    public String getBaselineBatchNo() {
        return baselineBatchNo;
    }

    public void setBaselineBatchNo(String baselineBatchNo) {
        this.baselineBatchNo = baselineBatchNo;
    }

    public BigDecimal getRetrievalHitRateDelta() {
        return retrievalHitRateDelta;
    }

    public void setRetrievalHitRateDelta(BigDecimal retrievalHitRateDelta) {
        this.retrievalHitRateDelta = retrievalHitRateDelta;
    }

    public BigDecimal getAnswerAccuracyRateDelta() {
        return answerAccuracyRateDelta;
    }

    public void setAnswerAccuracyRateDelta(BigDecimal answerAccuracyRateDelta) {
        this.answerAccuracyRateDelta = answerAccuracyRateDelta;
    }

    public BigDecimal getRecommendationReasonableRateDelta() {
        return recommendationReasonableRateDelta;
    }

    public void setRecommendationReasonableRateDelta(BigDecimal recommendationReasonableRateDelta) {
        this.recommendationReasonableRateDelta = recommendationReasonableRateDelta;
    }

    public BigDecimal getContextConsistencyRateDelta() {
        return contextConsistencyRateDelta;
    }

    public void setContextConsistencyRateDelta(BigDecimal contextConsistencyRateDelta) {
        this.contextConsistencyRateDelta = contextConsistencyRateDelta;
    }
}
