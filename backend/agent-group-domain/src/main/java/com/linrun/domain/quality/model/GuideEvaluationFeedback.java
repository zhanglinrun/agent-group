package com.linrun.domain.quality.model;

public class GuideEvaluationFeedback {

    private String targetType;
    private String priority;
    private String content;

    public GuideEvaluationFeedback() {
    }

    public GuideEvaluationFeedback(String targetType, String priority, String content) {
        this.targetType = targetType;
        this.priority = priority;
        this.content = content;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
