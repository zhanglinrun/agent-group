package com.linrun.domain.academic.runtime.agent;

import java.util.List;

final class AcademicPlanSteps {

    private AcademicPlanSteps() {
    }

    static List<AcademicPlanStep> copyAll(List<AcademicPlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .map(AcademicPlanStep::copy)
                .toList();
    }

    static List<String> ids(List<AcademicPlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .map(AcademicPlanStep::getStepId)
                .toList();
    }
}
