package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicAgentReflectionServiceTest {

    private final AcademicAgentReflectionService service = new AcademicAgentReflectionService();

    @Test
    void shouldReflectBlockedFirstStepWhenNoCompletedStepsExist() {
        AcademicPlanStep step = AcademicPlanStep.builder("S1", "读取论文材料")
                .order(1)
                .status(AcademicPlanLifecycleService.STATUS_BLOCKED)
                .note("工具调用失败")
                .build();
        AcademicAgentPlan plan = new AcademicAgentPlan("论文分析", List.of(step));

        AcademicAgentReflectionService.ReflectionResult result = service.reflect(plan, List.of());

        assertTrue(result.needReplan());
        assertTrue(result.getQuality() < 0.7d);
        assertEquals("较差", result.getQualityGrade());
        assertTrue(result.getImprovements().stream().anyMatch(item -> item.contains("执行失败")));
    }
}
