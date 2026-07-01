package com.linrun.domain.agent.runtime.agent;

import java.util.List;

public record AgentFlowReplanRequest(AgentPlan planSnapshot,
                                             AgentPlanStep failedStep,
                                             AgentStepExecutionResult failedResult,
                                             List<AgentPlanStep> completedSteps,
                                             int replanCount) {

    public AgentFlowReplanRequest {
        planSnapshot = planSnapshot == null ? new AgentPlan() : planSnapshot.copy();
        failedStep = failedStep == null ? null : failedStep.copy();
        completedSteps = completedSteps == null
                ? List.of()
                : completedSteps.stream().map(AgentPlanStep::copy).toList();
    }

    @Override
    public AgentPlan planSnapshot() {
        return planSnapshot.copy();
    }

    @Override
    public AgentPlanStep failedStep() {
        return failedStep == null ? null : failedStep.copy();
    }

    @Override
    public List<AgentPlanStep> completedSteps() {
        return completedSteps.stream().map(AgentPlanStep::copy).toList();
    }
}















