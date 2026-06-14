package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.agent.AcademicAgentFallbackReplanStrategy;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowReplanRequest;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentReplanStrategy;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能重规划策略。
 */
public class AcademicAgentIntelligentReplanStrategy implements AcademicAgentReplanStrategy {

    private final AcademicAgentFallbackReplanStrategy fallbackReplanStrategy = new AcademicAgentFallbackReplanStrategy();

    @Override
    public List<AcademicPlanStep> replan(AcademicAgentFlowReplanRequest request) {
        if (request == null || request.failedStep() == null) {
            return new ArrayList<>();
        }

        FailureAnalysis analysis = analyzeFailure(request);
        boolean canReuseCompleted = canReuseCompletedSteps(request.completedSteps(), analysis);

        if (canReuseCompleted && analysis.isRecoverable()) {
            return replanRemainingSteps(request, analysis);
        }
        return replanFromScratch(request);
    }

    private FailureAnalysis analyzeFailure(AcademicAgentFlowReplanRequest request) {
        AcademicAgentStepExecutionResult stepResult = request.failedResult();
        String failureNote = stepResult != null ? stepResult.note() : "未知错误";
        return analyzeFailureByRules(failureNote);
    }

    private FailureAnalysis analyzeFailureByRules(String failureNote) {
        String lowerNote = failureNote == null ? "" : failureNote.toLowerCase();

        if (lowerNote.contains("tool") && lowerNote.contains("not found")) {
            return new FailureAnalysis("工具不可用", true, true, "切换工具");
        }

        if (lowerNote.contains("parameter") || lowerNote.contains("invalid")) {
            return new FailureAnalysis("参数错误", true, true, "调整步骤");
        }

        if (lowerNote.contains("timeout") || lowerNote.contains("超时")) {
            return new FailureAnalysis("超时", true, true, "重试");
        }

        return new FailureAnalysis("未知错误", false, false, "从头开始");
    }

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

    private List<AcademicPlanStep> replanRemainingSteps(AcademicAgentFlowReplanRequest request,
                                                        FailureAnalysis analysis) {
        AcademicAgentPlan originalPlan = request.planSnapshot();
        AcademicPlanStep failedStep = request.failedStep();
        if (originalPlan == null || failedStep == null || !StringUtils.hasText(failedStep.getStepId())) {
            return replanFromScratch(request);
        }

        List<AcademicPlanStep> remainingSteps = getRemainingSteps(originalPlan, failedStep);
        AcademicPlanStep replacementStep;

        if ("切换工具".equals(analysis.getSuggestedStrategy())) {
            replacementStep = adjustStepForAlternativeTool(failedStep);
        } else if ("调整步骤".equals(analysis.getSuggestedStrategy())) {
            replacementStep = simplifyStep(failedStep);
        } else {
            replacementStep = failedStep.copy();
        }

        rerouteDependencies(remainingSteps, failedStep.getStepId(), replacementStep.getStepId());
        remainingSteps.add(0, replacementStep);

        return remainingSteps;
    }

    /**
     * 失败不可恢复或已完成步骤不可复用时，退回兜底策略重建剩余计划，
     * 至少产出一个恢复步骤交给执行引擎重试，而不是返回空列表导致整次运行直接失败。
     */
    private List<AcademicPlanStep> replanFromScratch(AcademicAgentFlowReplanRequest request) {
        return fallbackReplanStrategy.replan(request);
    }

    private List<AcademicPlanStep> getRemainingSteps(AcademicAgentPlan plan, AcademicPlanStep failedStep) {
        List<AcademicPlanStep> remaining = new ArrayList<>();
        if (plan == null || failedStep == null || !StringUtils.hasText(failedStep.getStepId())) {
            return remaining;
        }
        boolean foundFailed = false;

        for (AcademicPlanStep step : plan.getSteps()) {
            if (step == null || !StringUtils.hasText(step.getStepId())) {
                continue;
            }
            if (foundFailed) {
                remaining.add(step);
            }
            if (step.getStepId().equals(failedStep.getStepId())) {
                foundFailed = true;
            }
        }

        return remaining;
    }

    private void rerouteDependencies(List<AcademicPlanStep> steps, String oldStepId, String newStepId) {
        if (!StringUtils.hasText(oldStepId)
                || !StringUtils.hasText(newStepId)
                || oldStepId.equals(newStepId)) {
            return;
        }
        for (AcademicPlanStep step : steps) {
            if (step == null) {
                continue;
            }
            List<String> dependencies = step.getDependencies().stream()
                    .map(dependency -> oldStepId.equals(dependency) ? newStepId : dependency)
                    .toList();
            step.setDependencies(dependencies);
        }
    }

    private AcademicPlanStep adjustStepForAlternativeTool(AcademicPlanStep failedStep) {
        String newInstruction = failedStep.getInstruction() + "（使用替代方法或工具）";
        return AcademicPlanStep.builder(
                failedStep.getStepId() + "_retry",
                newInstruction)
                .order(failedStep.getOrder())
                .assignedAgent(failedStep.getAssignedAgent())
                .dependencies(failedStep.getDependencies())
                .build();
    }

    private AcademicPlanStep simplifyStep(AcademicPlanStep failedStep) {
        String newInstruction = failedStep.getInstruction() + "（简化版本）";
        return AcademicPlanStep.builder(
                failedStep.getStepId() + "_simplified",
                newInstruction)
                .order(failedStep.getOrder())
                .assignedAgent(failedStep.getAssignedAgent())
                .dependencies(failedStep.getDependencies())
                .build();
    }

    private static class FailureAnalysis {
        private final String failureType;
        private final boolean recoverable;
        private final boolean completedStepsValid;
        private final String suggestedStrategy;

        FailureAnalysis(String failureType, boolean recoverable,
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
