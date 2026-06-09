package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowReplanRequest;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentReplanStrategy;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能重规划策�?
 * 当步骤执行失败时，分析原因并动态生成新计划
 */
public class AcademicAgentIntelligentReplanStrategy implements AcademicAgentReplanStrategy {

    @Override
    public List<AcademicPlanStep> replan(AcademicAgentFlowReplanRequest request) {
        if (request == null || request.failedStep() == null) {
            return new ArrayList<>();
        }

        // 1. 分析失败原因
        FailureAnalysis analysis = analyzeFailure(request);

        // 2. 评估已完成步骤的价�?
        boolean canReuseCompleted = canReuseCompletedSteps(request.completedSteps(), analysis);

        // 3. 生成新计�?
        if (canReuseCompleted && analysis.isRecoverable()) {
            return replanRemainingSteps(request, analysis);
        } else {
            return replanFromScratch(request);
        }
    }

    /**
     * 分析失败原因
     */
    private FailureAnalysis analyzeFailure(AcademicAgentFlowReplanRequest request) {
        AcademicAgentStepExecutionResult stepResult = request.failedResult();
        String failureNote = stepResult != null ? stepResult.note() : "未知错误";
        return analyzeFailureByRules(failureNote);
    }

    /**
     * 基于规则分析失败原因
     */
    private FailureAnalysis analyzeFailureByRules(String failureNote) {
        String lowerNote = failureNote.toLowerCase();

        if (lowerNote.contains("tool") && lowerNote.contains("not found")) {
            return new FailureAnalysis("工具不可�?, true, true, "切换工具");
        }

        if (lowerNote.contains("parameter") || lowerNote.contains("invalid")) {
            return new FailureAnalysis("参数错误", true, true, "调整步骤");
        }

        if (lowerNote.contains("timeout") || lowerNote.contains("超时")) {
            return new FailureAnalysis("超时", true, true, "重试");
        }

        return new FailureAnalysis("未知错误", false, false, "从头开�?);
    }

    /**
     * 评估已完成步骤是否可复用
     */
    private boolean canReuseCompletedSteps(List<AcademicPlanStep> completedSteps, 
                                          FailureAnalysis analysis) {
        if (completedSteps == null || completedSteps.isEmpty()) {
            return false;
        }

        if (!analysis.isCompletedStepsValid()) {
            return false;
        }

        return completedSteps.stream()
                .allMatch(step -> AcademicPlanLifecycleService.STATUS_COMPLETED.equals(step.getStatus()));
    }

    /**
     * 重规划剩余步骤（复用已完成部分）
     */
    private List<AcademicPlanStep> replanRemainingSteps(AcademicAgentFlowReplanRequest request,
                                                        FailureAnalysis analysis) {
        AcademicAgentPlan originalPlan = request.planSnapshot();
        AcademicPlanStep failedStep = request.failedStep();

        List<AcademicPlanStep> remainingSteps = getRemainingSteps(originalPlan, failedStep);

        if ("切换工具".equals(analysis.getSuggestedStrategy())) {
            AcademicPlanStep adjustedStep = adjustStepForAlternativeTool(failedStep);
            remainingSteps.add(0, adjustedStep);
        } else if ("调整步骤".equals(analysis.getSuggestedStrategy())) {
            AcademicPlanStep simplifiedStep = simplifyStep(failedStep);
            remainingSteps.add(0, simplifiedStep);
        } else {
            remainingSteps.add(0, failedStep);
        }

        return remainingSteps;
    }

    /**
     * 从头重新规划
     */
    private List<AcademicPlanStep> replanFromScratch(AcademicAgentFlowReplanRequest request) {
        return new ArrayList<>();
    }

    /**
     * 获取失败步骤之后的所有步�?
     */
    private List<AcademicPlanStep> getRemainingSteps(AcademicAgentPlan plan, AcademicPlanStep failedStep) {
        List<AcademicPlanStep> remaining = new ArrayList<>();
        boolean foundFailed = false;

        for (AcademicPlanStep step : plan.getSteps()) {
            if (foundFailed) {
                remaining.add(step);
            }
            if (step.getStepId().equals(failedStep.getStepId())) {
                foundFailed = true;
            }
        }

        return remaining;
    }

    /**
     * 调整步骤使用替代工具
     */
    private AcademicPlanStep adjustStepForAlternativeTool(AcademicPlanStep failedStep) {
        String newInstruction = failedStep.getInstruction() + "（使用替代方法或工具�?;
        return AcademicPlanStep.builder(
                failedStep.getStepId() + "_retry",
                newInstruction)
                .order(failedStep.getOrder())
                .dependencies(failedStep.getDependencies())
                .build();
    }

    /**
     * 简化步�?
     */
    private AcademicPlanStep simplifyStep(AcademicPlanStep failedStep) {
        String newInstruction = failedStep.getInstruction() + "（简化版本）";
        return AcademicPlanStep.builder(
                failedStep.getStepId() + "_simplified",
                newInstruction)
                .order(failedStep.getOrder())
                .dependencies(failedStep.getDependencies())
                .build();
    }

    /**
     * 失败分析结果
     */
    private static class FailureAnalysis {
        private final String failureType;
        private final boolean recoverable;
        private final boolean completedStepsValid;
        private final String suggestedStrategy;

        public FailureAnalysis(String failureType, boolean recoverable, 
                              boolean completedStepsValid, String suggestedStrategy) {
            this.failureType = failureType;
            this.recoverable = recoverable;
            this.completedStepsValid = completedStepsValid;
            this.suggestedStrategy = suggestedStrategy;
        }

        public String getFailureType() {
            return failureType;
        }

        public boolean isRecoverable() {
            return recoverable;
        }

        public boolean isCompletedStepsValid() {
            return completedStepsValid;
        }

        public String getSuggestedStrategy() {
            return suggestedStrategy;
        }
    }
}















