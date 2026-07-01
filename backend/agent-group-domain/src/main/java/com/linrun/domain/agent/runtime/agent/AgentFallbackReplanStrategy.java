package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AgentFallbackReplanStrategy implements AgentReplanStrategy {

    private static final String RECOVERY_STEP_PREFIX = "R";
    private static final String DEFAULT_RECOVERY_AGENT = "replan-agent";

    @Override
    public List<AgentPlanStep> replan(AgentFlowReplanRequest request) {
        if (request == null || request.failedStep() == null) {
            return List.of();
        }
        AgentPlan plan = request.planSnapshot();
        AgentPlanStep failedStep = request.failedStep();
        String failedStepId = failedStep.getStepId();
        if (!StringUtils.hasText(failedStepId)) {
            return List.of();
        }

        Set<String> completedStepIds = stepIds(request.completedSteps());
        List<AgentPlanStep> planSteps = plan.getSteps();
        Set<String> usedStepIds = stepIds(planSteps);
        String recoveryStepId = nextRecoveryStepId(usedStepIds, request.replanCount());
        Set<String> remainingStepIds = remainingStepIds(planSteps, completedStepIds, failedStepId);

        List<AgentPlanStep> replanned = new ArrayList<>();
        replanned.add(recoveryStep(recoveryStepId, failedStep, request.failedResult(), completedStepIds));

        for (AgentPlanStep step : planSteps) {
            if (!shouldKeepRemainingStep(step, completedStepIds, failedStepId)) {
                continue;
            }
            AgentPlanStep copy = step.copy();
            copy.setStatus(AgentPlanLifecycleService.STATUS_NOT_STARTED);
            copy.setNote("");
            copy.setDependencies(rewriteDependencies(copy, completedStepIds, remainingStepIds,
                    failedStepId, recoveryStepId));
            replanned.add(copy);
        }
        normalizeOrder(replanned);
        return replanned;
    }

    private AgentPlanStep recoveryStep(String recoveryStepId,
                                          AgentPlanStep failedStep,
                                          AgentStepExecutionResult failedResult,
                                          Set<String> completedStepIds) {
        String note = failedResult == null ? "" : failedResult.note();
        String instruction = "Recover failed step " + failedStep.getStepId() + ": " + failedStep.getInstruction();
        if (StringUtils.hasText(note)) {
            instruction = instruction + ". Failure: " + note;
        }
        String assignedAgent = StringUtils.hasText(failedStep.getAssignedAgent())
                ? failedStep.getAssignedAgent()
                : DEFAULT_RECOVERY_AGENT;
        return AgentPlanStep.builder(recoveryStepId, instruction)
                .assignedAgent(assignedAgent)
                .dependencies(completedDependencies(failedStep, completedStepIds))
                .build();
    }

    private List<String> rewriteDependencies(AgentPlanStep step,
                                             Set<String> completedStepIds,
                                             Set<String> remainingStepIds,
                                             String failedStepId,
                                             String recoveryStepId) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (String dependency : step.getDependencies()) {
            String normalized = normalize(dependency);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.equals(failedStepId)) {
                dependencies.add(recoveryStepId);
            } else if (completedStepIds.contains(normalized) || remainingStepIds.contains(normalized)) {
                dependencies.add(normalized);
            }
        }
        return new ArrayList<>(dependencies);
    }

    private List<String> completedDependencies(AgentPlanStep step, Set<String> completedStepIds) {
        return step.getDependencies().stream()
                .map(this::normalize)
                .filter(completedStepIds::contains)
                .distinct()
                .toList();
    }

    private Set<String> remainingStepIds(List<AgentPlanStep> steps,
                                         Set<String> completedStepIds,
                                         String failedStepId) {
        LinkedHashSet<String> remainingStepIds = new LinkedHashSet<>();
        for (AgentPlanStep step : steps) {
            if (shouldKeepRemainingStep(step, completedStepIds, failedStepId)) {
                remainingStepIds.add(step.getStepId());
            }
        }
        return remainingStepIds;
    }

    private boolean shouldKeepRemainingStep(AgentPlanStep step,
                                            Set<String> completedStepIds,
                                            String failedStepId) {
        if (step == null || !StringUtils.hasText(step.getStepId())) {
            return false;
        }
        String stepId = step.getStepId();
        return !completedStepIds.contains(stepId) && !stepId.equals(failedStepId);
    }

    private Set<String> stepIds(List<AgentPlanStep> steps) {
        LinkedHashSet<String> stepIds = new LinkedHashSet<>();
        if (steps == null) {
            return stepIds;
        }
        for (AgentPlanStep step : steps) {
            if (step != null && StringUtils.hasText(step.getStepId())) {
                stepIds.add(step.getStepId());
            }
        }
        return stepIds;
    }

    private String nextRecoveryStepId(Set<String> usedStepIds, int replanCount) {
        int index = Math.max(1, replanCount + 1);
        String stepId = RECOVERY_STEP_PREFIX + index;
        while (usedStepIds.contains(stepId)) {
            index++;
            stepId = RECOVERY_STEP_PREFIX + index;
        }
        return stepId;
    }

    private void normalizeOrder(List<AgentPlanStep> steps) {
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setOrder(index + 1);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}















