package com.linrun.domain.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class AgentToolDefinition {

    private String name;
    private String description;
    private List<String> requiredArguments = new ArrayList<>();
    private List<String> optionalArguments = new ArrayList<>();
    private String version = "v1";
    private String riskLevel = "LOW";
    private long timeoutMillis = 3000L;
    private int maxRetries = 0;
    private boolean resultCitationRequired;
    private boolean idempotencyRequired;

    public AgentToolDefinition() {
    }

    public AgentToolDefinition(String name,
                               String description,
                               List<String> requiredArguments,
                               List<String> optionalArguments) {
        this.name = name;
        this.description = description;
        setRequiredArguments(requiredArguments);
        setOptionalArguments(optionalArguments);
    }

    public AgentToolDefinition(String name,
                               String description,
                               List<String> requiredArguments,
                               List<String> optionalArguments,
                               String version,
                               String riskLevel,
                               long timeoutMillis,
                               int maxRetries,
                               boolean resultCitationRequired,
                               boolean idempotencyRequired) {
        this(name, description, requiredArguments, optionalArguments);
        setVersion(version);
        setRiskLevel(riskLevel);
        setTimeoutMillis(timeoutMillis);
        setMaxRetries(maxRetries);
        this.resultCitationRequired = resultCitationRequired;
        this.idempotencyRequired = idempotencyRequired;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getRequiredArguments() {
        return requiredArguments;
    }

    public void setRequiredArguments(List<String> requiredArguments) {
        this.requiredArguments = requiredArguments == null ? new ArrayList<>() : new ArrayList<>(requiredArguments);
    }

    public List<String> getOptionalArguments() {
        return optionalArguments;
    }

    public void setOptionalArguments(List<String> optionalArguments) {
        this.optionalArguments = optionalArguments == null ? new ArrayList<>() : new ArrayList<>(optionalArguments);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null || version.isBlank() ? "v1" : version;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel == null || riskLevel.isBlank() ? "LOW" : riskLevel;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis <= 0L ? 3000L : timeoutMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public boolean isResultCitationRequired() {
        return resultCitationRequired;
    }

    public void setResultCitationRequired(boolean resultCitationRequired) {
        this.resultCitationRequired = resultCitationRequired;
    }

    public boolean isIdempotencyRequired() {
        return idempotencyRequired;
    }

    public void setIdempotencyRequired(boolean idempotencyRequired) {
        this.idempotencyRequired = idempotencyRequired;
    }
}
