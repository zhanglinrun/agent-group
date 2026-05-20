package com.linrun.domain.quality.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GuideEvaluationReport {

    private String batchNo;
    private String promptVersion;
    private String knowledgeVersion;
    private int totalCount;
    private BigDecimal retrievalHitRate;
    private BigDecimal answerAccuracyRate;
    private BigDecimal recommendationReasonableRate;
    private BigDecimal contextConsistencyRate;
    private long averageLatencyMillis;
    private long p99LatencyMillis;
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private BigDecimal estimatedCostYuan = BigDecimal.ZERO;
    private String baselineBatchNo;
    private BigDecimal retrievalHitRateDelta = BigDecimal.ZERO;
    private BigDecimal answerAccuracyRateDelta = BigDecimal.ZERO;
    private BigDecimal recommendationReasonableRateDelta = BigDecimal.ZERO;
    private BigDecimal contextConsistencyRateDelta = BigDecimal.ZERO;
    private List<GuideEvaluationItemResult> items = new ArrayList<>();
    private List<GuideEvaluationFeedback> feedbacks = new ArrayList<>();

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
        this.estimatedCostYuan = estimatedCostYuan == null ? BigDecimal.ZERO : estimatedCostYuan;
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
        this.retrievalHitRateDelta = retrievalHitRateDelta == null ? BigDecimal.ZERO : retrievalHitRateDelta;
    }

    public BigDecimal getAnswerAccuracyRateDelta() {
        return answerAccuracyRateDelta;
    }

    public void setAnswerAccuracyRateDelta(BigDecimal answerAccuracyRateDelta) {
        this.answerAccuracyRateDelta = answerAccuracyRateDelta == null ? BigDecimal.ZERO : answerAccuracyRateDelta;
    }

    public BigDecimal getRecommendationReasonableRateDelta() {
        return recommendationReasonableRateDelta;
    }

    public void setRecommendationReasonableRateDelta(BigDecimal recommendationReasonableRateDelta) {
        this.recommendationReasonableRateDelta = recommendationReasonableRateDelta == null
                ? BigDecimal.ZERO
                : recommendationReasonableRateDelta;
    }

    public BigDecimal getContextConsistencyRateDelta() {
        return contextConsistencyRateDelta;
    }

    public void setContextConsistencyRateDelta(BigDecimal contextConsistencyRateDelta) {
        this.contextConsistencyRateDelta = contextConsistencyRateDelta == null ? BigDecimal.ZERO : contextConsistencyRateDelta;
    }

    public List<GuideEvaluationItemResult> getItems() {
        return items;
    }

    public void setItems(List<GuideEvaluationItemResult> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public List<GuideEvaluationFeedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<GuideEvaluationFeedback> feedbacks) {
        this.feedbacks = feedbacks == null ? new ArrayList<>() : new ArrayList<>(feedbacks);
    }
}
