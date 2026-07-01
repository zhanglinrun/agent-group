package com.linrun.domain.agent.runtime.agent;

import java.util.List;

final class AgentPlanSteps {

    private AgentPlanSteps() {
    }

    static List<AgentPlanStep> copyAll(List<AgentPlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .map(AgentPlanStep::copy)
                .toList();
    }

    static List<String> ids(List<AgentPlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .map(AgentPlanStep::getStepId)
                .toList();
    }
}
