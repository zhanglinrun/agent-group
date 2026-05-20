package com.linrun.domain.conversation.model;

public class GuideUserInput {

    private String sessionId;
    private String userId;
    private String question;
    private String imageUrl;
    private String imageSummary;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageSummary() {
        return imageSummary;
    }

    public void setImageSummary(String imageSummary) {
        this.imageSummary = imageSummary;
    }
}
