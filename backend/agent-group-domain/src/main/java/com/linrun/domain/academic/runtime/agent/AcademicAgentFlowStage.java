package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public class AcademicAgentFlowStage {

    private final int stageIndex;
    private final List<AcademicPlanStep> steps;

    public AcademicAgentFlowStage(int stageIndex, List<AcademicPlanStep> steps) {
        this.stageIndex = Math.max(0, stageIndex);
        this.steps = steps == null ? List.of() : steps.stream()
                .map(AcademicPlanStep::copy)
                .toList();
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public List<AcademicPlanStep> getSteps() {
        return steps.stream()
                .map(AcademicPlanStep::copy)
                .toList();
    }

    public List<String> stepIds() {
        return steps.stream()
                .map(AcademicPlanStep::getStepId)
                .toList();
    }
}
