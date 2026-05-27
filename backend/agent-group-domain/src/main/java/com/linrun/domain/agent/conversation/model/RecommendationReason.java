package com.linrun.domain.agent.conversation.model;

public class RecommendationReason {

    private String reasonType;
    private String content;
    private int weight;

    public RecommendationReason() {
    }

    public RecommendationReason(String reasonType, String content, int weight) {
        this.reasonType = reasonType;
        this.content = content;
        this.weight = weight;
    }

    public String getReasonType() {
        return reasonType;
    }

    public void setReasonType(String reasonType) {
        this.reasonType = reasonType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
