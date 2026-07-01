package com.linrun.domain.agent.runtime.reasoning;

import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleService;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReflectionServiceTest {

    private final AgentReflectionService service = new AgentReflectionService();

    @Test
    void shouldReflectBlockedFirstStepWhenNoCompletedStepsExist() {
        AgentPlanStep step = AgentPlanStep.builder("S1", "读取论文材料")
                .order(1)
                .status(AgentPlanLifecycleService.STATUS_BLOCKED)
                .note("工具调用失败")
                .build();
        AgentPlan plan = new AgentPlan("论文分析", List.of(step));

        AgentReflectionService.ReflectionResult result = service.reflect(plan, List.of());

        assertTrue(result.needReplan());
        assertTrue(result.getQuality() < 0.7d);
        assertEquals("较差", result.getQualityGrade());
        assertTrue(result.getImprovements().stream().anyMatch(item -> item.contains("执行失败")));
    }
}
