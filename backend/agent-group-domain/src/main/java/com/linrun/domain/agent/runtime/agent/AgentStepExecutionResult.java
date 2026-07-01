package com.linrun.domain.agent.runtime.agent;

public record AgentStepExecutionResult(boolean success, String note) {

    public static AgentStepExecutionResult success(String note) {
        return new AgentStepExecutionResult(true, AgentValues.safe(note));
    }

    public static AgentStepExecutionResult failed(String note) {
        return new AgentStepExecutionResult(false, AgentValues.safe(note));
    }
}















