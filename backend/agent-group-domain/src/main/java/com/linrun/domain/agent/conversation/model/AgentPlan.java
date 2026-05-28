package com.linrun.domain.agent.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class AgentPlan {

    private GuideIntentType intent;
    private List<AgentToolCall> tools = new ArrayList<>();
    private String answerPolicy;
    private List<AgentSkill> skills = new ArrayList<>();
    private List<AgentExecutionStage> executionStages = new ArrayList<>();
    private boolean clarificationRequired;
    private String critique;

    public GuideIntentType getIntent() {
        return intent;
    }

    public void setIntent(GuideIntentType intent) {
        this.intent = intent;
    }

    public List<AgentToolCall> getTools() {
        return tools;
    }

    public void setTools(List<AgentToolCall> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public String getAnswerPolicy() {
        return answerPolicy;
    }

    public void setAnswerPolicy(String answerPolicy) {
        this.answerPolicy = answerPolicy;
    }

    public List<AgentSkill> getSkills() {
        return skills;
    }

    public void setSkills(List<AgentSkill> skills) {
        this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
    }

    public List<AgentExecutionStage> getExecutionStages() {
        return executionStages;
    }

    public void setExecutionStages(List<AgentExecutionStage> executionStages) {
        this.executionStages = executionStages == null ? new ArrayList<>() : new ArrayList<>(executionStages);
    }

    public boolean isClarificationRequired() {
        return clarificationRequired;
    }

    public void setClarificationRequired(boolean clarificationRequired) {
        this.clarificationRequired = clarificationRequired;
    }

    public String getCritique() {
        return critique;
    }

    public void setCritique(String critique) {
        this.critique = critique;
    }

    public boolean hasTool(String toolName) {
        return tools.stream().anyMatch(tool -> toolName.equals(tool.getName()));
    }

    public List<String> toolNames() {
        return tools.stream().map(AgentToolCall::getName).toList();
    }
}
