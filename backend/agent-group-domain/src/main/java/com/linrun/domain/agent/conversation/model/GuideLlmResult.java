package com.linrun.domain.agent.conversation.model;

public class GuideLlmResult {

    private String content;
    private GuideTokenUsage tokenUsage = GuideTokenUsage.empty();
    private long latencyMillis;
    private boolean fallbackUsed;
    private String model;

    public GuideLlmResult() {
    }

    public GuideLlmResult(String content, GuideTokenUsage tokenUsage, long latencyMillis,
                          boolean fallbackUsed, String model) {
        this.content = content;
        this.tokenUsage = tokenUsage == null ? GuideTokenUsage.empty() : tokenUsage;
        this.latencyMillis = Math.max(0L, latencyMillis);
        this.fallbackUsed = fallbackUsed;
        this.model = model;
    }

    public static GuideLlmResult of(String content, GuideTokenUsage tokenUsage, long latencyMillis,
                                    boolean fallbackUsed, String model) {
        return new GuideLlmResult(content, tokenUsage, latencyMillis, fallbackUsed, model);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public GuideTokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(GuideTokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage == null ? GuideTokenUsage.empty() : tokenUsage;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(long latencyMillis) {
        this.latencyMillis = Math.max(0L, latencyMillis);
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
