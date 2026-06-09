package com.linrun.domain.academic.runtime.agent;

@FunctionalInterface
public interface AcademicAgentStepExecutor {

    AcademicAgentStepExecutionResult execute(AcademicPlanStep step,
                                             AcademicAgentFlowExecutionContext context);
}















