package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AcademicAgentFlowProgressProjector {

    private final AcademicAgentFlowProjector flowProjector;

    public AcademicAgentFlowProgressProjector() {
        this(new AcademicAgentFlowProjector());
    }

    public AcademicAgentFlowProgressProjector(AcademicAgentFlowProjector flowProjector) {
        this.flowProjector = flowProjector == null ? new AcademicAgentFlowProjector() : flowProjector;
    }

    public AcademicAgentFlowProgressResult start(AcademicAgentPlan plan) {
        return start(plan, "stage started");
    }

    public AcademicAgentFlowProgressResult start(AcademicAgentPlan plan, String message) {
        List<AcademicAgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AcademicAgentFlowProgressResult(List.of(), -1);
        }
        AcademicAgentFlowStage first = stages.getFirst();
        String runningMessage = StringUtils.hasText(message) ? message.trim() : "stage started";
        return new AcademicAgentFlowProgressResult(
                List.of(progress(first, AcademicAgentFlowProgress.STATUS_RUNNING, runningMessage)),
                first.getStageIndex());
    }

    public AcademicAgentFlowProgressResult advanceToTool(AcademicAgentPlan plan,
                                                         int currentStageIndex,
                                                         String toolName) {
        List<AcademicAgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int targetStageIndex = toolStageIndex(stages, currentStageIndex, toolName);
        String message = StringUtils.hasText(toolName)
                ? "tool started: " + toolName.trim()
                : "tool started";
        return advanceToStage(stages, currentStageIndex, targetStageIndex, message);
    }

    public AcademicAgentFlowProgressResult completeRemaining(AcademicAgentPlan plan,
                                                             int currentStageIndex) {
        List<AcademicAgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int startIndex = Math.max(0, currentStageIndex);
        List<AcademicAgentFlowProgress> events = new ArrayList<>();
        if (currentStageIndex < 0) {
            events.add(progress(stages.getFirst(), AcademicAgentFlowProgress.STATUS_RUNNING, "stage started"));
        }
        for (int index = startIndex; index < stages.size(); index++) {
            events.add(progress(stages.get(index), AcademicAgentFlowProgress.STATUS_COMPLETED, "stage completed"));
        }
        return new AcademicAgentFlowProgressResult(events, stages.size());
    }

    public AcademicAgentFlowProgressResult blockCurrent(AcademicAgentPlan plan,
                                                        int currentStageIndex,
                                                        String reason) {
        List<AcademicAgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int stageIndex = currentStageIndex < 0 ? 0 : Math.min(currentStageIndex, stages.size() - 1);
        String message = StringUtils.hasText(reason) ? reason.trim() : "stage blocked";
        return new AcademicAgentFlowProgressResult(
                List.of(progress(stages.get(stageIndex), AcademicAgentFlowProgress.STATUS_BLOCKED, message)),
                stageIndex);
    }

    public AcademicAgentFlowProgressResult markReplanned(AcademicAgentPlan plan,
                                                         int currentStageIndex,
                                                         String reason) {
        List<AcademicAgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int stageIndex = currentStageIndex < 0 ? 0 : Math.min(currentStageIndex, stages.size() - 1);
        String message = StringUtils.hasText(reason) ? reason.trim() : "stage replanned";
        return new AcademicAgentFlowProgressResult(
                List.of(progress(stages.get(stageIndex), AcademicAgentFlowProgress.STATUS_REPLANNED, message)),
                stageIndex);
    }

    private AcademicAgentFlowProgressResult advanceToStage(List<AcademicAgentFlowStage> stages,
                                                           int currentStageIndex,
                                                           int targetStageIndex,
                                                           String runningMessage) {
        if (targetStageIndex < 0 || targetStageIndex >= stages.size()) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        if (targetStageIndex <= currentStageIndex) {
            return new AcademicAgentFlowProgressResult(List.of(), currentStageIndex);
        }
        List<AcademicAgentFlowProgress> events = new ArrayList<>();
        int start = Math.max(0, currentStageIndex);
        if (currentStageIndex < 0) {
            events.add(progress(stages.getFirst(), AcademicAgentFlowProgress.STATUS_RUNNING, "stage started"));
        }
        for (int index = start; index < targetStageIndex; index++) {
            events.add(progress(stages.get(index), AcademicAgentFlowProgress.STATUS_COMPLETED, "stage completed"));
        }
        events.add(progress(stages.get(targetStageIndex), AcademicAgentFlowProgress.STATUS_RUNNING, runningMessage));
        return new AcademicAgentFlowProgressResult(events, targetStageIndex);
    }

    private int toolStageIndex(List<AcademicAgentFlowStage> stages,
                               int currentStageIndex,
                               String toolName) {
        int nextIndex = currentStageIndex + 1;
        if (nextIndex < stages.size()) {
            return nextIndex;
        }
        return currentStageIndex < 0 && !stages.isEmpty() ? 0 : currentStageIndex;
    }

    private List<AcademicAgentFlowStage> stages(AcademicAgentPlan plan) {
        return plan == null ? List.of() : flowProjector.buildRemainingStages(plan);
    }

    private AcademicAgentFlowProgress progress(AcademicAgentFlowStage stage, String status, String message) {
        return new AcademicAgentFlowProgress(stage, status, message);
    }
}
