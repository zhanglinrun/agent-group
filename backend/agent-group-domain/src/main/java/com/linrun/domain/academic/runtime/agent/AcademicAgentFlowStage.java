package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public class AcademicAgentFlowStage {

    private final int stageIndex;
    private final List<AcademicPlanStep> steps;

    public AcademicAgentFlowStage(int stageIndex, List<AcademicPlanStep> steps) {
        this.stageIndex = Math.max(0, stageIndex);
        this.steps = AcademicPlanSteps.copyAll(steps);
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public List<AcademicPlanStep> getSteps() {
        return AcademicPlanSteps.copyAll(steps);
    }

    public List<String> stepIds() {
        return AcademicPlanSteps.ids(steps);
    }
}















