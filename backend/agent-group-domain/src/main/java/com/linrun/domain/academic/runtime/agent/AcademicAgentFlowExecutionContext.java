package com.linrun.domain.academic.runtime.agent;

public record AcademicAgentFlowExecutionContext(String runId,
                                                int stageIndex,
                                                int replanCount,
                                                AcademicAgentPlan planSnapshot) {

    public AcademicAgentFlowExecutionContext {
        runId = runId == null ? "" : runId.trim();
        planSnapshot = planSnapshot == null ? new AcademicAgentPlan() : planSnapshot.copy();
    }

    @Override
    public AcademicAgentPlan planSnapshot() {
        return planSnapshot.copy();
    }
}















