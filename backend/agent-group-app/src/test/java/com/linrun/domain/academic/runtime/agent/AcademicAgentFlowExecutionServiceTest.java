package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicAgentFlowExecutionServiceTest {

    @Test
    void shouldExecutePlanByDependencyStages() {
        AcademicAgentPlan plan = new AcademicAgentPlan("论文实验分析", List.of(
                step("S1", "读取论文摘要", 1),
                step("S2", "分析方法设计", 2, "S1"),
                step("S3", "分析实验指标", 2, "S1"),
                step("S4", "生成报告", 3, "S2", "S3")
        ));
        AcademicAgentFlowExecutionService service = new AcademicAgentFlowExecutionService(
                new AcademicAgentFlowProjector(), 0);
        List<String> executed = new ArrayList<>();

        AcademicAgentFlowExecutionResult result = service.execute("RUN1001", plan, (step, context) -> {
            executed.add(context.stageIndex() + ":" + step.getStepId());
            return AcademicAgentStepExecutionResult.success("done " + step.getStepId());
        }, null);

        assertTrue(result.isCompleted());
        assertEquals(List.of("0:S1", "1:S2", "1:S3", "2:S4"), executed);
        assertEquals(3, result.getEvents().stream()
                .filter(event -> AcademicAgentFlowExecutionEvent.TYPE_STAGE_STARTED.equals(event.getEventType()))
                .count());
        assertTrue(result.getFinalPlan().getSteps().stream().allMatch(AcademicPlanStep::isCompleted));
    }

    @Test
    void shouldReplanRemainingStepsWhenExecutionIsBlocked() {
        AcademicAgentPlan plan = new AcademicAgentPlan("论文实验指标复核", List.of(
                step("S1", "读取论文摘要", 1),
                step("S2", "调用不可用数据源", 2, "S1"),
                step("S3", "整理结论", 3, "S2")
        ));
        AcademicAgentFlowExecutionService service = new AcademicAgentFlowExecutionService(
                new AcademicAgentFlowProjector(), 1);
        AtomicBoolean failedOnce = new AtomicBoolean(false);

        AcademicAgentFlowExecutionResult result = service.execute("RUN1002", plan, (step, context) -> {
            if ("S2".equals(step.getStepId()) && failedOnce.compareAndSet(false, true)) {
                return AcademicAgentStepExecutionResult.failed("数据源不可用");
            }
            return AcademicAgentStepExecutionResult.success("done " + step.getStepId());
        }, request -> List.of(
                step("R1", "改查实验结果行, 2),
                step("R2", "整理指标差异结论", 3, "R1")
        ));

        assertTrue(result.isCompleted());
        assertEquals(1, result.getReplanCount());
        assertEquals(List.of("S1", "R1", "R2"), result.getFinalPlan().getSteps().stream()
                .map(AcademicPlanStep::getStepId)
                .toList());
        assertEquals(1, result.getEvents().stream()
                .filter(event -> AcademicAgentFlowExecutionEvent.TYPE_REPLANNED.equals(event.getEventType()))
                .count());
        assertTrue(result.getFinalPlan().getSteps().stream().allMatch(AcademicPlanStep::isCompleted));
    }

    private AcademicPlanStep step(String stepId, String instruction, int order, String... dependencies) {
        return AcademicPlanStep.builder(stepId, instruction)
                .order(order)
                .dependencies(List.of(dependencies))
                .build();
    }
}















