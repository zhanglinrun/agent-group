package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowReplanRequest;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicAgentIntelligentReplanStrategyTest {

    private final AcademicAgentIntelligentReplanStrategy strategy = new AcademicAgentIntelligentReplanStrategy();

    @Test
    void shouldRerouteDownstreamDependenciesWhenFailedStepIdChanges() {
        AcademicPlanStep completed = step("S1", "读取论文摘要", 1);
        completed.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
        AcademicPlanStep failed = step("S2", "调用检索工具", 2, "S1");
        failed.setAssignedAgent("retriever");
        AcademicPlanStep downstream = step("S3", "整理结论", 3, "S2");
        AcademicAgentPlan plan = new AcademicAgentPlan("论文分析", List.of(completed, failed, downstream));

        List<AcademicPlanStep> replanned = strategy.replan(new AcademicAgentFlowReplanRequest(
                plan,
                failed,
                AcademicAgentStepExecutionResult.failed("tool not found"),
                List.of(completed),
                0));

        assertEquals(List.of("S2_retry", "S3"), replanned.stream()
                .map(AcademicPlanStep::getStepId)
                .toList());
        assertEquals(List.of("S1"), replanned.getFirst().getDependencies());
        assertEquals(List.of("S2_retry"), replanned.get(1).getDependencies());
        assertEquals("retriever", replanned.getFirst().getAssignedAgent());
    }

    private AcademicPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AcademicPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}
