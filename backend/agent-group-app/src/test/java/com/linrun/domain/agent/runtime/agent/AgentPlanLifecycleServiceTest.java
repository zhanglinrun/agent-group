package com.linrun.domain.agent.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanLifecycleServiceTest {

    private final AgentPlanLifecycleService lifecycleService = new AgentPlanLifecycleService();

    @Test
    void shouldCreatePlanAndActivateFirstStep() {
        AgentPlanLifecycleResult result = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指标", "生成报告"));

        assertTrue(result.isAutoAdvanced());
        assertEquals(0, result.getCurrentStepIndex());
        assertEquals("S1", result.getCurrentStep().getStepId());
        assertEquals(AgentPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().getFirst().getStatus());
    }

    @Test
    void shouldCompleteCurrentStepAndAdvanceNextStep() {
        AgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指标")).getPlan();

        AgentPlanLifecycleResult result = lifecycleService.markStep(
                plan, 0, AgentPlanLifecycleService.STATUS_COMPLETED, "论文方法已梳理");

        assertTrue(result.isAutoAdvanced());
        assertEquals(1, result.getCurrentStepIndex());
        assertEquals(AgentPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals(AgentPlanLifecycleService.STATUS_IN_PROGRESS,
                result.getPlan().getSteps().get(1).getStatus());
    }

    @Test
    void shouldReplanRemainingStepsAndKeepCompletedPrefix() {
        AgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指标", "生成报告")).getPlan();
        AgentPlan afterFirstStep = lifecycleService.markStep(
                plan, 0, AgentPlanLifecycleService.STATUS_COMPLETED, "论文方法已梳理").getPlan();

        AgentPlanLifecycleResult result = lifecycleService.updateRemaining(
                afterFirstStep, "补充实验指标验证", List.of("补充消融实验对比", "输出实验分析报告"));

        assertEquals(3, result.getPlan().getSteps().size());
        assertEquals("梳理论文方法", result.getPlan().getSteps().getFirst().getInstruction());
        assertEquals(AgentPlanLifecycleService.STATUS_COMPLETED,
                result.getPlan().getSteps().getFirst().getStatus());
        assertEquals("补充消融实验对比", result.getCurrentStep().getInstruction());
    }

    @Test
    void shouldRejectCompletingNonCurrentStep() {
        AgentPlan plan = lifecycleService.create("研究 RAG 论文实验结果",
                List.of("梳理论文方法", "检查实验指标")).getPlan();

        assertThrows(IllegalStateException.class,
                () -> lifecycleService.markStep(plan, 1, AgentPlanLifecycleService.STATUS_COMPLETED, ""));
    }
}















