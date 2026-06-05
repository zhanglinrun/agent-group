package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public class AcademicAgentFlowProgressResult {

    private final List<AcademicAgentFlowProgress> events;
    private final int currentStageIndex;

    public AcademicAgentFlowProgressResult(List<AcademicAgentFlowProgress> events,
                                           int currentStageIndex) {
        this.events = events == null ? List.of() : List.copyOf(events);
        this.currentStageIndex = currentStageIndex;
    }

    public List<AcademicAgentFlowProgress> getEvents() {
        return events;
    }

    public int getCurrentStageIndex() {
        return currentStageIndex;
    }
}
