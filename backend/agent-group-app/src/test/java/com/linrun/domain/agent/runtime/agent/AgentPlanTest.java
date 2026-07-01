package com.linrun.domain.agent.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlanTest {

    @Test
    void shouldProtectPlanStepsWithDefensiveCopies() {
        AgentPlanStep source = AgentPlanStep.builder("S1", "read paper abstract")
                .order(1)
                .build();
        List<AgentPlanStep> steps = new ArrayList<>(List.of(source));

        AgentPlan plan = new AgentPlan("paper analysis", steps);
        source.setInstruction("external change");
        steps.clear();

        assertEquals(1, plan.getSteps().size());
        assertEquals("read paper abstract", plan.getSteps().getFirst().getInstruction());

        AgentPlanStep returned = plan.getSteps().getFirst();
        returned.setInstruction("returned change");

        assertEquals("read paper abstract", plan.getSteps().getFirst().getInstruction());
    }
}
