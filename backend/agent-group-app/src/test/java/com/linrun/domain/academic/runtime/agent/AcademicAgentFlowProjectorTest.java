package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcademicAgentFlowProjectorTest {

    private final AcademicAgentFlowProjector flowProjector = new AcademicAgentFlowProjector();

    @Test
    void shouldBuildParallelStagesByDependencies() {
        AcademicAgentPlan plan = new AcademicAgentPlan("并行研究", List.of(
                step("S1", "读取论文摘要", 1),
                step("S2", "查询实验指标", 1),
                step("S3", "生成报告", 2, "S1", "S2")
        ));

        List<AcademicAgentFlowStage> stages = flowProjector.buildRemainingStages(plan);

        assertEquals(2, stages.size());
        assertEquals(List.of("S1", "S2"), stages.getFirst().stepIds());
        assertEquals(List.of("S3"), stages.get(1).stepIds());
    }

    @Test
    void shouldSkipCompletedDependenciesWhenFindingNextExecutableSteps() {
        AcademicPlanStep first = step("S1", "读取论文摘要", 1);
        first.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
        AcademicPlanStep second = step("S2", "生成报告", 2, "S1");
        AcademicAgentPlan plan = new AcademicAgentPlan("串行研究", List.of(first, second));

        assertEquals(List.of("S2"), flowProjector.nextExecutableSteps(plan).stream()
                .map(AcademicPlanStep::getStepId)
                .toList());
    }

    @Test
    void shouldRejectUnknownDependency() {
        AcademicAgentPlan plan = new AcademicAgentPlan("异常流程", List.of(
                step("S1", "生成报告", 1, "S404")
        ));

        assertThrows(IllegalArgumentException.class, () -> flowProjector.buildRemainingStages(plan));
    }

    @Test
    void shouldRejectCyclicDependency() {
        AcademicAgentPlan plan = new AcademicAgentPlan("循环流程", List.of(
                step("S1", "读取论文摘要", 1, "S2"),
                step("S2", "查询实验指标", 1, "S1")
        ));

        assertThrows(IllegalStateException.class, () -> flowProjector.buildRemainingStages(plan));
    }

    private AcademicPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AcademicPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}
