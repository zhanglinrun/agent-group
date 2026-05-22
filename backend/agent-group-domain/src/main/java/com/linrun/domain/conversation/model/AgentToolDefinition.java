package com.linrun.domain.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class AgentToolDefinition {

    private String name;
    private String description;
    private List<String> requiredArguments = new ArrayList<>();
    private List<String> optionalArguments = new ArrayList<>();

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
}
