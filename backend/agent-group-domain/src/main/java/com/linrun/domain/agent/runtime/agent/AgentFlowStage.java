package com.linrun.domain.agent.runtime.agent;

import java.util.List;

public class AgentFlowStage {

    private final int stageIndex;
    private final List<AgentPlanStep> steps;

    public AgentFlowStage(int stageIndex, List<AgentPlanStep> steps) {
        this.stageIndex = Math.max(0, stageIndex);
        this.steps = AgentPlanSteps.copyAll(steps);
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public List<AgentPlanStep> getSteps() {
        return AgentPlanSteps.copyAll(steps);
    }

    public List<String> stepIds() {
        return AgentPlanSteps.ids(steps);
    }
}















