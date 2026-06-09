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
        AcademicPlanLifecycleResult result = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指核, "生成报告"));

        assertTrue(result.isAutoAdvanced());
        assertEquals(0, result.getCurrentStepIndex());
        assertEquals("S1", result.getCurrentStep().getStepId());
        assertEquals(AcademicPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().getFirst().getStatus());
    }

    @Test
    void shouldCompleteCurrentStepAndAdvanceNextStep() {
        AcademicAgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指核)).getPlan();

        AcademicPlanLifecycleResult result = lifecycleService.markStep(
                plan, 0, AcademicPlanLifecycleService.STATUS_COMPLETED, "论文方法已梳理);

        assertTrue(result.isAutoAdvanced());
        assertEquals(1, result.getCurrentStepIndex());
        assertEquals(AcademicPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals(AcademicPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().get(1).getStatus());
    }

    @Test
    void shouldReplanRemainingStepsAndKeepCompletedPrefix() {
        AcademicAgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指核, "生成报告")).getPlan();
        AcademicAgentPlan afterFirstStep = lifecycleService.markStep(
                plan, 0, AcademicPlanLifecycleService.STATUS_COMPLETED, "论文方法已梳理).getPlan();

        AcademicPlanLifecycleResult result = lifecycleService.updateRemaining(
                afterFirstStep, "补充实验指标验证", List.of("补充消融实验对比", "输出实验分析报告"));

        assertEquals(3, result.getPlan().getSteps().size());
        assertEquals("梳理论文方法", result.getPlan().getSteps().getFirst().getInstruction());
        assertEquals(AcademicPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals("补充消融实验对比", result.getCurrentStep().getInstruction());
    }

    @Test
    void shouldRejectCompletingNonCurrentStep() {
        AcademicAgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指核)).getPlan();

        assertThrows(IllegalStateException.class,
                () -> lifecycleService.markStep(plan, 1, AcademicPlanLifecycleService.STATUS_COMPLETED, ""));
    }
}















