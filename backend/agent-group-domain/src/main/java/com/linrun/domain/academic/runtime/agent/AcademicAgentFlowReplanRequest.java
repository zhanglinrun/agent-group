package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public record AcademicAgentFlowReplanRequest(AcademicAgentPlan planSnapshot,
                                             AcademicPlanStep failedStep,
                                             AcademicAgentStepExecutionResult failedResult,
                                             List<AcademicPlanStep> completedSteps,
                                             int replanCount) {

    public AcademicAgentFlowReplanRequest {
        planSnapshot = planSnapshot == null ? new AcademicAgentPlan() : planSnapshot.copy();
        failedStep = failedStep == null ? null : failedStep.copy();
        completedSteps = completedSteps == null
                ? List.of()
                : completedSteps.stream().map(AcademicPlanStep::copy).toList();
    }

    @Override
    public AcademicAgentPlan planSnapshot() {
        return planSnapshot.copy();
    }

    @Override
    public AcademicPlanStep failedStep() {
        return failedStep == null ? null : failedStep.copy();
    }

    @Override
    public List<AcademicPlanStep> completedSteps() {
        return completedSteps.stream().map(AcademicPlanStep::copy).toList();
    }
}















