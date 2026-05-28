package com.linrun.domain.agent.conversation.model;

public class AgentExecutionStage {

    private String stage;
    private String objective;
    private String status;
    private String evidenceRequired;

    public AgentExecutionStage() {
    }

    public AgentExecutionStage(String stage, String objective, String status, String evidenceRequired) {
        this.stage = stage;
        this.objective = objective;
        this.status = status;
        this.evidenceRequired = evidenceRequired;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEvidenceRequired() {
        return evidenceRequired;
    }

    public void setEvidenceRequired(String evidenceRequired) {
        this.evidenceRequired = evidenceRequired;
    }
}
