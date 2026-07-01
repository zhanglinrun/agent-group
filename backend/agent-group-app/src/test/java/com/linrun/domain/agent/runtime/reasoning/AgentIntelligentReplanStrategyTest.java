package com.linrun.domain.agent.runtime.reasoning;

import com.linrun.domain.agent.runtime.agent.AgentFlowReplanRequest;
import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentStepExecutionResult;
import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleService;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntelligentReplanStrategyTest {

    private final AgentIntelligentReplanStrategy strategy = new AgentIntelligentReplanStrategy();

    @Test
    void shouldRerouteDownstreamDependenciesWhenFailedStepIdChanges() {
        AgentPlanStep completed = step("S1", "读取论文摘要", 1);
        completed.setStatus(AgentPlanLifecycleService.STATUS_COMPLETED);
        AgentPlanStep failed = step("S2", "调用检索工具", 2, "S1");
        failed.setAssignedAgent("retriever");
        AgentPlanStep downstream = step("S3", "整理结论", 3, "S2");
        AgentPlan plan = new AgentPlan("论文分析", List.of(completed, failed, downstream));

        List<AgentPlanStep> replanned = strategy.replan(new AgentFlowReplanRequest(
                plan,
                failed,
                AgentStepExecutionResult.failed("tool not found"),
                List.of(completed),
                0));

        assertEquals(List.of("S2_retry", "S3"), replanned.stream()
                .map(AgentPlanStep::getStepId)
                .toList());
        assertEquals(List.of("S1"), replanned.getFirst().getDependencies());
        assertEquals(List.of("S2_retry"), replanned.get(1).getDependencies());
        assertEquals("retriever", replanned.getFirst().getAssignedAgent());
    }

    private AgentPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AgentPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}
