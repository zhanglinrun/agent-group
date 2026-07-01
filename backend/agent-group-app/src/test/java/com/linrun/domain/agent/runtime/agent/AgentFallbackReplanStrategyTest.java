package com.linrun.domain.agent.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFallbackReplanStrategyTest {

    private final AgentFallbackReplanStrategy strategy = new AgentFallbackReplanStrategy();

    @Test
    void shouldInsertRecoveryStepAndRerouteFailedDependencies() {
        AgentPlanStep completed = step("S1", "read order", 1);
        completed.setStatus(AgentPlanLifecycleService.STATUS_COMPLETED);
        AgentPlanStep failed = step("S2", "query settlement", 2, "S1");
        AgentPlanStep downstream = step("S3", "write report", 3, "S2");
        AgentPlan plan = new AgentPlan("trade data", List.of(completed, failed, downstream));

        List<AgentPlanStep> replanned = strategy.replan(new AgentFlowReplanRequest(
                plan,
                failed,
                AgentStepExecutionResult.failed("settlement source timeout"),
                List.of(completed),
                0));

        assertEquals(List.of("R1", "S3"), replanned.stream().map(AgentPlanStep::getStepId).toList());
        assertEquals(List.of("S1"), replanned.getFirst().getDependencies());
        assertEquals(List.of("R1"), replanned.get(1).getDependencies());
        assertTrue(replanned.getFirst().getInstruction().contains("settlement source timeout"));
    }

    @Test
    void shouldUseUniqueRecoveryStepIdWhenPlanAlreadyContainsRecoveryId() {
        AgentPlanStep completed = step("S1", "read order", 1);
        completed.setStatus(AgentPlanLifecycleService.STATUS_COMPLETED);
        AgentPlanStep failed = step("S2", "query settlement", 2, "S1");
        AgentPlanStep existingRecoveryId = step("R1", "write report", 3, "S2");
        AgentPlan plan = new AgentPlan("trade data", List.of(completed, failed, existingRecoveryId));

        List<AgentPlanStep> replanned = strategy.replan(new AgentFlowReplanRequest(
                plan,
                failed,
                AgentStepExecutionResult.failed("settlement source timeout"),
                List.of(completed),
                0));

        assertEquals("R2", replanned.getFirst().getStepId());
        assertEquals(List.of("R2"), replanned.get(1).getDependencies());
    }

    private AgentPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AgentPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}















