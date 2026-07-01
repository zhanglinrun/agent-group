package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AgentPlanLifecycleService {

    public static final String STATUS_NOT_STARTED = "not_started";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_BLOCKED = "blocked";

    public AgentPlanLifecycleResult create(String title, List<String> steps) {
        AgentPlan plan = AgentPlan.create(title, steps);
        boolean autoAdvanced = activateFirstNotStarted(plan);
        return result(plan, autoAdvanced, false);
    }

    public AgentPlanLifecycleResult updateRemaining(AgentPlan plan,
                                                       String title,
                                                       List<String> remainingSteps) {
        validatePlan(plan);
        validateSteps(remainingSteps);
        AgentPlan nextPlan = plan.copy();
        normalize(nextPlan);
        if (StringUtils.hasText(title)) {
            nextPlan.setTitle(title);
        }

        int completedPrefixSize = countCompletedPrefix(nextPlan);
        List<AgentPlanStep> merged = new ArrayList<>();
        for (int index = 0; index < completedPrefixSize; index++) {
            AgentPlanStep completed = nextPlan.mutableSteps().get(index).copy();
            completed.setStatus(STATUS_COMPLETED);
            merged.add(completed);
        }
        for (int index = 0; index < remainingSteps.size(); index++) {
            merged.add(AgentPlanStep.builder("S" + (completedPrefixSize + index + 1), remainingSteps.get(index))
                    .order(completedPrefixSize + index + 1)
                    .build());
        }
        nextPlan.setSteps(merged);
        return ensureExecutable(nextPlan);
    }

    public AgentPlanLifecycleResult markStep(AgentPlan plan,
                                                int stepIndex,
                                                String status,
                                                String note) {
        validatePlan(plan);
        validateStatus(status);
        AgentPlan nextPlan = plan.copy();
        normalize(nextPlan);
        if (stepIndex < 0 || stepIndex >= nextPlan.mutableSteps().size()) {
            throw new IllegalArgumentException("invalid plan step index: " + stepIndex);
        }

        AgentPlanStep step = nextPlan.mutableSteps().get(stepIndex);
        if (step.isCompleted() && !STATUS_COMPLETED.equals(status)) {
            throw new IllegalStateException("completed step cannot be changed");
        }
        int currentIndex = nextPlan.currentStepIndex();
        if (STATUS_COMPLETED.equals(status) && currentIndex >= 0 && currentIndex != stepIndex) {
            throw new IllegalStateException("only current step can be completed");
        }

        step.setStatus(status);
        step.setNote(note);
        if (!STATUS_COMPLETED.equals(status)) {
            return ensureExecutable(nextPlan);
        }
        if (nextPlan.allCompleted()) {
            return result(nextPlan, false, true);
        }
        boolean autoAdvanced = activateFirstNotStarted(nextPlan);
        return result(nextPlan, autoAdvanced, false);
    }

    public AgentPlanLifecycleResult finish(AgentPlan plan) {
        AgentPlan nextPlan = plan == null ? new AgentPlan() : plan.copy();
        normalize(nextPlan);
        for (AgentPlanStep step : nextPlan.mutableSteps()) {
            step.setStatus(STATUS_COMPLETED);
        }
        return result(nextPlan, false, true);
    }

    public AgentPlanLifecycleResult ensureExecutable(AgentPlan plan) {
        validatePlan(plan);
        AgentPlan nextPlan = plan.copy();
        normalize(nextPlan);
        if (nextPlan.allCompleted()) {
            return result(nextPlan, false, true);
        }
        if (nextPlan.currentStepIndex() >= 0) {
            return result(nextPlan, false, false);
        }
        boolean autoAdvanced = activateFirstNotStarted(nextPlan);
        if (!autoAdvanced) {
            throw new IllegalStateException("plan has no executable step");
        }
        return result(nextPlan, true, false);
    }

    public boolean isAllStepsCompleted(AgentPlan plan) {
        return plan == null || plan.copy().allCompleted();
    }

    private void normalize(AgentPlan plan) {
        int inProgressCount = 0;
        for (AgentPlanStep step : plan.mutableSteps()) {
            validateStatus(step.getStatus());
            if (STATUS_IN_PROGRESS.equals(step.getStatus())) {
                inProgressCount++;
                if (inProgressCount > 1) {
                    step.setStatus(STATUS_NOT_STARTED);
                }
            }
        }
    }

    private boolean activateFirstNotStarted(AgentPlan plan) {
        for (AgentPlanStep step : plan.mutableSteps()) {
            if (STATUS_IN_PROGRESS.equals(step.getStatus())) {
                step.setStatus(STATUS_NOT_STARTED);
            }
        }
        for (AgentPlanStep step : plan.mutableSteps()) {
            if (STATUS_NOT_STARTED.equals(step.getStatus())) {
                step.setStatus(STATUS_IN_PROGRESS);
                return true;
            }
        }
        return false;
    }

    private int countCompletedPrefix(AgentPlan plan) {
        int count = 0;
        for (AgentPlanStep step : plan.mutableSteps()) {
            if (!step.isCompleted()) {
                break;
            }
            count++;
        }
        return count;
    }

    private AgentPlanLifecycleResult result(AgentPlan plan,
                                               boolean autoAdvanced,
                                               boolean autoFinished) {
        return new AgentPlanLifecycleResult(
                plan,
                plan.currentStep().orElse(null),
                plan.currentStepIndex(),
                autoAdvanced,
                autoFinished);
    }

    private void validatePlan(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalStateException("plan does not exist");
        }
    }

    private void validateSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("remaining plan steps cannot be empty");
        }
        for (String step : steps) {
            if (!StringUtils.hasText(step)) {
                throw new IllegalArgumentException("plan step cannot be blank");
            }
        }
    }

    private void validateStatus(String status) {
        if (!STATUS_NOT_STARTED.equals(status)
                && !STATUS_IN_PROGRESS.equals(status)
                && !STATUS_COMPLETED.equals(status)
                && !STATUS_BLOCKED.equals(status)) {
            throw new IllegalArgumentException("invalid plan step status: " + status);
        }
    }
}















