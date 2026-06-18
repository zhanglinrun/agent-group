package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicAgentPlanTest {

    @Test
    void shouldProtectPlanStepsWithDefensiveCopies() {
        AcademicPlanStep source = AcademicPlanStep.builder("S1", "read paper abstract")
                .order(1)
                .build();
        List<AcademicPlanStep> steps = new ArrayList<>(List.of(source));

        AcademicAgentPlan plan = new AcademicAgentPlan("paper analysis", steps);
        source.setInstruction("external change");
        steps.clear();

        assertEquals(1, plan.getSteps().size());
        assertEquals("read paper abstract", plan.getSteps().getFirst().getInstruction());

        AcademicPlanStep returned = plan.getSteps().getFirst();
        returned.setInstruction("returned change");

        assertEquals("read paper abstract", plan.getSteps().getFirst().getInstruction());
    }
}
