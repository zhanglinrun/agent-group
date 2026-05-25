package com.linrun.domain.conversation.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentToolCall {

    private String name;
    private Map<String, String> arguments = new LinkedHashMap<>();
    private String reason;
    private String toolVersion;
    private String riskLevel;
    private Boolean resultCitationRequired;

    public AgentToolCall() {
    }

    public AgentToolCall(String name, Map<String, String> arguments, String reason) {
        this.name = name;
        setArguments(arguments);
        this.reason = reason;
    }

    public static AgentToolCall of(String name, Map<String, String> arguments, String reason) {
        return new AgentToolCall(name, arguments, reason);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getResultCitationRequired() {
        return resultCitationRequired;
    }

    public void setResultCitationRequired(Boolean resultCitationRequired) {
        this.resultCitationRequired = resultCitationRequired;
    }
}
