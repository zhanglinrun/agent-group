package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AcademicAgentFlowExecutionService {

    private final AcademicAgentFlowProjector flowProjector;
    private final int maxReplanAttempts;

    public AcademicAgentFlowExecutionService() {
        this(new AcademicAgentFlowProjector(), 1);
    }

    public AcademicAgentFlowExecutionService(AcademicAgentFlowProjector flowProjector,
                                             int maxReplanAttempts) {
        this.flowProjector = flowProjector == null ? new AcademicAgentFlowProjector() : flowProjector;
        this.maxReplanAttempts = Math.max(0, maxReplanAttempts);
    }

    public AcademicAgentFlowExecutionResult execute(String runId,
                                                    AcademicAgentPlan plan,
                                                    AcademicAgentStepExecutor stepExecutor,
                                                    AcademicAgentReplanStrategy replanStrategy) {
        if (plan == null) {
            throw new IllegalArgumentException("agent flow plan cannot be null");
        }
        if (stepExecutor == null) {
            throw new IllegalArgumentException("agent step executor cannot be null");
        }
        AcademicAgentPlan currentPlan = plan.copy();
        List<AcademicAgentFlowExecutionEvent> events = new ArrayList<>();
        int replanCount = 0;
        int executionStageIndex = 0;

        while (!currentPlan.allCompleted()) {
            List<AcademicAgentFlowStage> stages = flowProjector.buildRemainingStages(currentPlan);
            if (stages.isEmpty()) {
                break;
            }
            AcademicAgentFlowStage stage = stages.getFirst();
            int stageIndex = executionStageIndex++;
            events.add(new AcademicAgentFlowExecutionEvent(
                    AcademicAgentFlowExecutionEvent.TYPE_STAGE_STARTED,
                    stageIndex, "", "", ""));

            boolean replanned = false;
            for (AcademicPlanStep stageStep : stage.getSteps()) {
                AcademicPlanStep step = mutableStep(currentPlan, stageStep.getStepId());
                step.setStatus(AcademicPlanLifecycleService.STATUS_IN_PROGRESS);
                events.add(stepEvent(AcademicAgentFlowExecutionEvent.TYPE_STEP_STARTED, stageIndex, step, ""));

                AcademicAgentStepExecutionResult stepResult = executeStep(runId, currentPlan, stepExecutor,
                        replanCount, stageIndex, step);
                if (stepResult.success()) {
                    step.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
                    step.setNote(stepResult.note());
                    events.add(stepEvent(AcademicAgentFlowExecutionEvent.TYPE_STEP_COMPLETED,
                            stageIndex, step, stepResult.note()));
                    continue;
                }

                step.setStatus(AcademicPlanLifecycleService.STATUS_BLOCKED);
                step.setNote(stepResult.note());
                events.add(stepEvent(AcademicAgentFlowExecutionEvent.TYPE_STEP_BLOCKED,
                        stageIndex, step, stepResult.note()));

                if (replanStrategy != null && replanCount < maxReplanAttempts) {
                    List<AcademicPlanStep> remaining = replanStrategy.replan(new AcademicAgentFlowReplanRequest(
                            currentPlan, step, stepResult, completedSteps(currentPlan), replanCount));
                    if (remaining != null && !remaining.isEmpty()) {
                        currentPlan = rebuildAfterReplan(currentPlan, remaining);
                        replanCount++;
                        events.add(new AcademicAgentFlowExecutionEvent(
                                AcademicAgentFlowExecutionEvent.TYPE_REPLANNED,
                                stageIndex, step.getStepId(), step.getInstruction(), stepResult.note()));
                        replanned = true;
                        break;
                    }
                }
                return new AcademicAgentFlowExecutionResult(currentPlan, events, replanCount, false);
            }
            if (!replanned) {
                continue;
            }
        }
        return new AcademicAgentFlowExecutionResult(currentPlan, events, replanCount, currentPlan.allCompleted());
    }

    private AcademicAgentStepExecutionResult executeStep(String runId,
                                                        AcademicAgentPlan currentPlan,
                                                        AcademicAgentStepExecutor stepExecutor,
                                                        int replanCount,
                                                        int stageIndex,
                                                        AcademicPlanStep step) {
        AcademicAgentFlowExecutionContext context = new AcademicAgentFlowExecutionContext(
                runId, stageIndex, replanCount, currentPlan);
        AcademicAgentStepExecutionResult result = stepExecutor.execute(step.copy(), context);
        return result == null ? AcademicAgentStepExecutionResult.failed("step executor returned empty result") : result;
    }

    private AcademicPlanStep mutableStep(AcademicAgentPlan plan, String stepId) {
        for (AcademicPlanStep step : plan.mutableSteps()) {
            if (step.getStepId().equals(stepId)) {
                return step;
            }
        }
        throw new IllegalArgumentException("plan step not found: " + stepId);
    }

    private List<AcademicPlanStep> completedSteps(AcademicAgentPlan plan) {
        return plan.mutableSteps().stream()
                .filter(AcademicPlanStep::isCompleted)
                .map(AcademicPlanStep::copy)
                .toList();
    }

    private AcademicAgentPlan rebuildAfterReplan(AcademicAgentPlan currentPlan,
                                                 List<AcademicPlanStep> replannedRemaining) {
        List<AcademicPlanStep> merged = new ArrayList<>();
        Set<String> usedStepIds = new LinkedHashSet<>();
        for (AcademicPlanStep completed : completedSteps(currentPlan)) {
            AcademicPlanStep copy = completed.copy();
            copy.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
            copy.setOrder(merged.size() + 1);
            merged.add(copy);
            usedStepIds.add(copy.getStepId());
        }
        for (AcademicPlanStep remaining : replannedRemaining) {
            AcademicPlanStep copy = remaining.copy();
            if (!StringUtils.hasText(copy.getStepId()) || usedStepIds.contains(copy.getStepId())) {
                copy.setStepId("S" + (merged.size() + 1));
            }
            copy.setStatus(AcademicPlanLifecycleService.STATUS_NOT_STARTED);
            copy.setOrder(merged.size() + 1);
            merged.add(copy);
            usedStepIds.add(copy.getStepId());
        }
        return new AcademicAgentPlan(currentPlan.getTitle(), merged);
    }

    private AcademicAgentFlowExecutionEvent stepEvent(String eventType,
                                                     int stageIndex,
                                                     AcademicPlanStep step,
                                                     String note) {
        return new AcademicAgentFlowExecutionEvent(eventType, stageIndex,
                step.getStepId(), step.getInstruction(), note);
    }
}
