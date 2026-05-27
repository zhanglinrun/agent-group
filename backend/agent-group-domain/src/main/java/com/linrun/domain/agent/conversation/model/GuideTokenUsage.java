package com.linrun.domain.agent.conversation.model;

import java.math.BigDecimal;

public class GuideTokenUsage {

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private BigDecimal estimatedCostYuan = BigDecimal.ZERO;

    public GuideTokenUsage() {
    }

    public GuideTokenUsage(long promptTokens, long completionTokens, long totalTokens, BigDecimal estimatedCostYuan) {
        this.promptTokens = Math.max(0L, promptTokens);
        this.completionTokens = Math.max(0L, completionTokens);
        this.totalTokens = totalTokens > 0 ? totalTokens : this.promptTokens + this.completionTokens;
        this.estimatedCostYuan = estimatedCostYuan == null ? BigDecimal.ZERO : estimatedCostYuan;
    }

    public static GuideTokenUsage empty() {
        return new GuideTokenUsage();
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = Math.max(0L, promptTokens);
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = Math.max(0L, completionTokens);
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = Math.max(0L, totalTokens);
    }

    public BigDecimal getEstimatedCostYuan() {
        return estimatedCostYuan;
    }

    public void setEstimatedCostYuan(BigDecimal estimatedCostYuan) {
        this.estimatedCostYuan = estimatedCostYuan == null ? BigDecimal.ZERO : estimatedCostYuan;
    }
}
