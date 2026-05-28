package com.linrun.infrastructure.po;

import java.math.BigDecimal;

public class GuideEvaluationItemResultPO {

    private String caseId;
    private String caseName;
    private String question;
    private String expectedGoodsId;
    private String actualGoodsId;
    private boolean referencePassed;
    private boolean answerPassed;
    private boolean recommendationPassed;
    private boolean contextPassed;
    private String actualToolNames;
    private boolean toolCallPassed;
    private boolean toolArgumentPassed;
    private boolean toolResultReferencePassed;
    private long latencyMillis;
    private long llmLatencyMillis;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private BigDecimal estimatedCostYuan;
    private boolean fallbackUsed;
    private int score;
    private String suggestion;

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getExpectedGoodsId() {
        return expectedGoodsId;
    }

    public void setExpectedGoodsId(String expectedGoodsId) {
        this.expectedGoodsId = expectedGoodsId;
    }

    public String getActualGoodsId() {
        return actualGoodsId;
    }

    public void setActualGoodsId(String actualGoodsId) {
        this.actualGoodsId = actualGoodsId;
    }

    public boolean isReferencePassed() {
        return referencePassed;
    }

    public void setReferencePassed(boolean referencePassed) {
        this.referencePassed = referencePassed;
    }

    public boolean isAnswerPassed() {
        return answerPassed;
    }

    public void setAnswerPassed(boolean answerPassed) {
        this.answerPassed = answerPassed;
    }

    public boolean isRecommendationPassed() {
        return recommendationPassed;
    }

    public void setRecommendationPassed(boolean recommendationPassed) {
        this.recommendationPassed = recommendationPassed;
    }

    public boolean isContextPassed() {
        return contextPassed;
    }

    public void setContextPassed(boolean contextPassed) {
        this.contextPassed = contextPassed;
    }

    public String getActualToolNames() {
        return actualToolNames;
    }

    public void setActualToolNames(String actualToolNames) {
        this.actualToolNames = actualToolNames;
    }

    public boolean isToolCallPassed() {
        return toolCallPassed;
    }

    public void setToolCallPassed(boolean toolCallPassed) {
        this.toolCallPassed = toolCallPassed;
    }

    public boolean isToolArgumentPassed() {
        return toolArgumentPassed;
    }

    public void setToolArgumentPassed(boolean toolArgumentPassed) {
        this.toolArgumentPassed = toolArgumentPassed;
    }

    public boolean isToolResultReferencePassed() {
        return toolResultReferencePassed;
    }

    public void setToolResultReferencePassed(boolean toolResultReferencePassed) {
        this.toolResultReferencePassed = toolResultReferencePassed;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public long getLlmLatencyMillis() {
        return llmLatencyMillis;
    }

    public void setLlmLatencyMillis(long llmLatencyMillis) {
        this.llmLatencyMillis = llmLatencyMillis;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
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

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
