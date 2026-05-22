package com.linrun.domain.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class AgentPlan {

    private GuideIntentType intent;
    private List<AgentToolCall> tools = new ArrayList<>();
    private String answerPolicy;

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

    public boolean hasTool(String toolName) {
        return tools.stream().anyMatch(tool -> toolName.equals(tool.getName()));
    }

    public List<String> toolNames() {
        return tools.stream().map(AgentToolCall::getName).toList();
    }
}
