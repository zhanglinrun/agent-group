package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicPlanLifecycleServiceTest {

    private final AcademicPlanLifecycleService lifecycleService = new AcademicPlanLifecycleService();

    @Test
    void shouldCreatePlanAndActivateFirstStep() {
        AcademicPlanLifecycleResult result = lifecycleService.create("研究拼团交易链路",
                List.of("梳理订单状态", "检查额度发放", "生成报告"));

        assertTrue(result.isAutoAdvanced());
        assertEquals(0, result.getCurrentStepIndex());
        assertEquals("S1", result.getCurrentStep().getStepId());
        assertEquals(AcademicPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().getFirst().getStatus());
    }

    @Test
    void shouldCompleteCurrentStepAndAdvanceNextStep() {
        AcademicAgentPlan plan = lifecycleService.create("研究拼团交易链路",
                List.of("梳理订单状态", "检查额度发放")).getPlan();

        AcademicPlanLifecycleResult result = lifecycleService.markStep(
                plan, 0, AcademicPlanLifecycleService.STATUS_COMPLETED, "订单状态已完成");

        assertTrue(result.isAutoAdvanced());
        assertEquals(1, result.getCurrentStepIndex());
        assertEquals(AcademicPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals(AcademicPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().get(1).getStatus());
    }

    @Test
    void shouldReplanRemainingStepsAndKeepCompletedPrefix() {
        AcademicAgentPlan plan = lifecycleService.create("研究拼团交易链路",
                List.of("梳理订单状态", "检查额度发放", "生成报告")).getPlan();
        AcademicAgentPlan afterFirstStep = lifecycleService.markStep(
                plan, 0, AcademicPlanLifecycleService.STATUS_COMPLETED, "订单状态已完成").getPlan();

        AcademicPlanLifecycleResult result = lifecycleService.updateRemaining(
                afterFirstStep, "补充交易一致性验证", List.of("补充退款补偿检查", "输出交易一致性报告"));

        assertEquals(3, result.getPlan().getSteps().size());
        assertEquals("梳理订单状态", result.getPlan().getSteps().getFirst().getInstruction());
        assertEquals(AcademicPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals("补充退款补偿检查", result.getCurrentStep().getInstruction());
    }

    @Test
    void shouldRejectCompletingNonCurrentStep() {
        AcademicAgentPlan plan = lifecycleService.create("研究拼团交易链路",
                List.of("梳理订单状态", "检查额度发放")).getPlan();

        assertThrows(IllegalStateException.class,
                () -> lifecycleService.markStep(plan, 1, AcademicPlanLifecycleService.STATUS_COMPLETED, ""));
    }
}
