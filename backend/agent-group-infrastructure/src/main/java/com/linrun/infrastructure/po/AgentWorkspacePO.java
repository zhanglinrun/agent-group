package com.linrun.infrastructure.po;

import java.time.LocalDateTime;

public class AgentWorkspacePO {

    private String projectId;
    private String userId;
    private String title;
    private String researchQuestion;
    private String targetVenue;
    private String writingStatus;
    private String progressNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getResearchQuestion() {
        return researchQuestion;
    }

    public void setResearchQuestion(String researchQuestion) {
        this.researchQuestion = researchQuestion;
    }

    public String getTargetVenue() {
        return targetVenue;
    }

    public void setTargetVenue(String targetVenue) {
        this.targetVenue = targetVenue;
    }

    public String getWritingStatus() {
        return writingStatus;
    }

    public void setWritingStatus(String writingStatus) {
        this.writingStatus = writingStatus;
    }

    public String getProgressNote() {
        return progressNote;
    }

    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}















