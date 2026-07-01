package com.linrun.domain.agent.runtime.agent;

import java.util.List;

public class AgentFlowProgressResult {

    private final List<AgentFlowProgress> events;
    private final int currentStageIndex;

    public AgentFlowProgressResult(List<AgentFlowProgress> events,
                                           int currentStageIndex) {
        this.events = events == null ? List.of() : List.copyOf(events);
        this.currentStageIndex = currentStageIndex;
    }

    public List<AgentFlowProgress> getEvents() {
        return events;
    }

    public int getCurrentStageIndex() {
        return currentStageIndex;
    }
}















