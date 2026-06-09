package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public class AcademicAgentFlowExecutionResult {

    private final AcademicAgentPlan finalPlan;
    private final List<AcademicAgentFlowExecutionEvent> events;
    private final int replanCount;
    private final boolean completed;

    public AcademicAgentFlowExecutionResult(AcademicAgentPlan finalPlan,
                                            List<AcademicAgentFlowExecutionEvent> events,
                                            int replanCount,
                                            boolean completed) {
        this.finalPlan = finalPlan == null ? new AcademicAgentPlan() : finalPlan.copy();
        this.events = events == null ? List.of() : List.copyOf(events);
        this.replanCount = Math.max(0, replanCount);
        this.completed = completed;
    }

    public AcademicAgentPlan getFinalPlan() {
        return finalPlan.copy();
    }

    public List<AcademicAgentFlowExecutionEvent> getEvents() {
        return events;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public boolean isCompleted() {
        return completed;
    }
}















