package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AgentFlowProgressProjector {

    private final AgentFlowProjector flowProjector;

    public AgentFlowProgressProjector() {
        this(new AgentFlowProjector());
    }

    public AgentFlowProgressProjector(AgentFlowProjector flowProjector) {
        this.flowProjector = flowProjector == null ? new AgentFlowProjector() : flowProjector;
    }

    public AgentFlowProgressResult start(AgentPlan plan) {
        return start(plan, "stage started");
    }

    public AgentFlowProgressResult start(AgentPlan plan, String message) {
        List<AgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AgentFlowProgressResult(List.of(), -1);
        }
        AgentFlowStage first = stages.getFirst();
        String runningMessage = StringUtils.hasText(message) ? message.trim() : "stage started";
        return new AgentFlowProgressResult(
                List.of(progress(first, AgentFlowProgress.STATUS_RUNNING, runningMessage)),
                first.getStageIndex());
    }

    public AgentFlowProgressResult advanceToTool(AgentPlan plan,
                                                         int currentStageIndex,
                                                         String toolName) {
        List<AgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int targetStageIndex = toolStageIndex(stages, currentStageIndex, toolName);
        String message = StringUtils.hasText(toolName)
                ? "tool started: " + toolName.trim()
                : "tool started";
        return advanceToStage(stages, currentStageIndex, targetStageIndex, message);
    }

    public AgentFlowProgressResult completeRemaining(AgentPlan plan,
                                                             int currentStageIndex) {
        List<AgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int startIndex = Math.max(0, currentStageIndex);
        List<AgentFlowProgress> events = new ArrayList<>();
        if (currentStageIndex < 0) {
            events.add(progress(stages.getFirst(), AgentFlowProgress.STATUS_RUNNING, "stage started"));
        }
        for (int index = startIndex; index < stages.size(); index++) {
            events.add(progress(stages.get(index), AgentFlowProgress.STATUS_COMPLETED, "stage completed"));
        }
        return new AgentFlowProgressResult(events, stages.size());
    }

    public AgentFlowProgressResult blockCurrent(AgentPlan plan,
                                                        int currentStageIndex,
                                                        String reason) {
        List<AgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int stageIndex = currentStageIndex < 0 ? 0 : Math.min(currentStageIndex, stages.size() - 1);
        String message = StringUtils.hasText(reason) ? reason.trim() : "stage blocked";
        return new AgentFlowProgressResult(
                List.of(progress(stages.get(stageIndex), AgentFlowProgress.STATUS_BLOCKED, message)),
                stageIndex);
    }

    public AgentFlowProgressResult markReplanned(AgentPlan plan,
                                                         int currentStageIndex,
                                                         String reason) {
        List<AgentFlowStage> stages = stages(plan);
        if (stages.isEmpty()) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        int stageIndex = currentStageIndex < 0 ? 0 : Math.min(currentStageIndex, stages.size() - 1);
        String message = StringUtils.hasText(reason) ? reason.trim() : "stage replanned";
        return new AgentFlowProgressResult(
                List.of(progress(stages.get(stageIndex), AgentFlowProgress.STATUS_REPLANNED, message)),
                stageIndex);
    }

    private AgentFlowProgressResult advanceToStage(List<AgentFlowStage> stages,
                                                           int currentStageIndex,
                                                           int targetStageIndex,
                                                           String runningMessage) {
        if (targetStageIndex < 0 || targetStageIndex >= stages.size()) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        if (targetStageIndex <= currentStageIndex) {
            return new AgentFlowProgressResult(List.of(), currentStageIndex);
        }
        List<AgentFlowProgress> events = new ArrayList<>();
        int start = Math.max(0, currentStageIndex);
        if (currentStageIndex < 0) {
            events.add(progress(stages.getFirst(), AgentFlowProgress.STATUS_RUNNING, "stage started"));
        }
        for (int index = start; index < targetStageIndex; index++) {
            events.add(progress(stages.get(index), AgentFlowProgress.STATUS_COMPLETED, "stage completed"));
        }
        events.add(progress(stages.get(targetStageIndex), AgentFlowProgress.STATUS_RUNNING, runningMessage));
        return new AgentFlowProgressResult(events, targetStageIndex);
    }

    private int toolStageIndex(List<AgentFlowStage> stages,
                               int currentStageIndex,
                               String toolName) {
        int nextIndex = currentStageIndex + 1;
        if (nextIndex < stages.size()) {
            return nextIndex;
        }
        return currentStageIndex < 0 && !stages.isEmpty() ? 0 : currentStageIndex;
    }

    private List<AgentFlowStage> stages(AgentPlan plan) {
        return plan == null ? List.of() : flowProjector.buildRemainingStages(plan);
    }

    private AgentFlowProgress progress(AgentFlowStage stage, String status, String message) {
        return new AgentFlowProgress(stage, status, message);
    }
}















