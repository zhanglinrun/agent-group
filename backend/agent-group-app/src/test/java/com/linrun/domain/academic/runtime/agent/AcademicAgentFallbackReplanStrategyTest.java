package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicAgentFallbackReplanStrategyTest {

    private final AcademicAgentFallbackReplanStrategy strategy = new AcademicAgentFallbackReplanStrategy();

    @Test
    void shouldInsertRecoveryStepAndRerouteFailedDependencies() {
        AcademicPlanStep completed = step("S1", "read order", 1);
        completed.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
        AcademicPlanStep failed = step("S2", "query settlement", 2, "S1");
        AcademicPlanStep downstream = step("S3", "write report", 3, "S2");
        AcademicAgentPlan plan = new AcademicAgentPlan("trade audit", List.of(completed, failed, downstream));

        List<AcademicPlanStep> replanned = strategy.replan(new AcademicAgentFlowReplanRequest(
                plan,
                failed,
                AcademicAgentStepExecutionResult.failed("settlement source timeout"),
                List.of(completed),
                0));

        assertEquals(List.of("R1", "S3"), replanned.stream().map(AcademicPlanStep::getStepId).toList());
        assertEquals(List.of("S1"), replanned.getFirst().getDependencies());
        assertEquals(List.of("R1"), replanned.get(1).getDependencies());
        assertTrue(replanned.getFirst().getInstruction().contains("settlement source timeout"));
    }

    @Test
    void shouldUseUniqueRecoveryStepIdWhenPlanAlreadyContainsRecoveryId() {
        AcademicPlanStep completed = step("S1", "read order", 1);
        completed.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
        AcademicPlanStep failed = step("S2", "query settlement", 2, "S1");
        AcademicPlanStep existingRecoveryId = step("R1", "write report", 3, "S2");
        AcademicAgentPlan plan = new AcademicAgentPlan("trade audit", List.of(completed, failed, existingRecoveryId));

        List<AcademicPlanStep> replanned = strategy.replan(new AcademicAgentFlowReplanRequest(
                plan,
                failed,
                AcademicAgentStepExecutionResult.failed("settlement source timeout"),
                List.of(completed),
                0));

        assertEquals("R2", replanned.getFirst().getStepId());
        assertEquals(List.of("R2"), replanned.get(1).getDependencies());
    }

    @Test
    void shouldUseFallbackReplanStrategyWhenNoStrategyIsProvided() {
        AcademicAgentPlan plan = new AcademicAgentPlan("fallback replan", List.of(
                step("S1", "read order", 1),
                step("S2", "query unavailable source", 2, "S1"),
                step("S3", "write final report", 3, "S2")
        ));
        AcademicAgentFlowExecutionService service = new AcademicAgentFlowExecutionService(
                new AcademicAgentFlowProjector(), 1);
        AtomicBoolean failedOnce = new AtomicBoolean(false);

        AcademicAgentFlowExecutionResult result = service.execute("RUN1003", plan, (step, context) -> {
            if ("S2".equals(step.getStepId()) && failedOnce.compareAndSet(false, true)) {
                return AcademicAgentStepExecutionResult.failed("source unavailable");
            }
            return AcademicAgentStepExecutionResult.success("done " + step.getStepId());
        }, null);

        List<AcademicPlanStep> finalSteps = result.getFinalPlan().getSteps();
        assertTrue(result.isCompleted());
        assertEquals(1, result.getReplanCount());
        assertEquals(List.of("S1", "R1", "S3"), finalSteps.stream()
                .map(AcademicPlanStep::getStepId)
                .toList());
        assertEquals(List.of("R1"), finalSteps.get(2).getDependencies());
        assertTrue(finalSteps.get(1).getInstruction().contains("source unavailable"));
    }

    private AcademicPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AcademicPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}
