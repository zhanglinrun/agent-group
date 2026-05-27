package com.linrun.domain.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class GuideRagAnswerResult {

    private List<String> segments = new ArrayList<>();
    private GuideTokenUsage tokenUsage = GuideTokenUsage.empty();
    private long llmLatencyMillis;
    private boolean fallbackUsed;
    private String model;
    private GuideAnswerReflection reflection = GuideAnswerReflection.passed();

    public GuideRagAnswerResult() {
    }

    public GuideRagAnswerResult(List<String> segments, GuideTokenUsage tokenUsage, long llmLatencyMillis,
                                boolean fallbackUsed, String model) {
        this.segments = segments == null ? new ArrayList<>() : new ArrayList<>(segments);
        this.tokenUsage = tokenUsage == null ? GuideTokenUsage.empty() : tokenUsage;
        this.llmLatencyMillis = Math.max(0L, llmLatencyMillis);
        this.fallbackUsed = fallbackUsed;
        this.model = model;
    }

    public List<String> getSegments() {
        return segments;
    }

    public void setSegments(List<String> segments) {
        this.segments = segments == null ? new ArrayList<>() : new ArrayList<>(segments);
    }

    public GuideTokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(GuideTokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage == null ? GuideTokenUsage.empty() : tokenUsage;
    }

    public long getLlmLatencyMillis() {
        return llmLatencyMillis;
    }

    public void setLlmLatencyMillis(long llmLatencyMillis) {
        this.llmLatencyMillis = Math.max(0L, llmLatencyMillis);
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

    public GuideAnswerReflection getReflection() {
        return reflection;
    }

    public void setReflection(GuideAnswerReflection reflection) {
        this.reflection = reflection == null ? GuideAnswerReflection.passed() : reflection;
    }
}
