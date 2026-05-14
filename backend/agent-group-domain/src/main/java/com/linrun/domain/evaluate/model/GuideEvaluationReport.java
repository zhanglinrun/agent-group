package com.linrun.domain.evaluate.model;

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

    public List<GuideEvaluationItemResult> getItems() {
        return items;
    }

    public void setItems(List<GuideEvaluationItemResult> items) {
        this.items = items;
    }

    public List<GuideEvaluationFeedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<GuideEvaluationFeedback> feedbacks) {
        this.feedbacks = feedbacks == null ? new ArrayList<>() : new ArrayList<>(feedbacks);
    }
}
