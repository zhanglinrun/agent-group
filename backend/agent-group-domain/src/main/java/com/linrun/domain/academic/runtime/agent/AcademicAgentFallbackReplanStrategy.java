package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AcademicAgentFallbackReplanStrategy implements AcademicAgentReplanStrategy {

    private static final String RECOVERY_STEP_PREFIX = "R";
    private static final String DEFAULT_RECOVERY_AGENT = "replan-agent";

    @Override
    public List<AcademicPlanStep> replan(AcademicAgentFlowReplanRequest request) {
        if (request == null || request.failedStep() == null) {
            return List.of();
        }
        AcademicAgentPlan plan = request.planSnapshot();
        AcademicPlanStep failedStep = request.failedStep();
        String failedStepId = failedStep.getStepId();
        if (!StringUtils.hasText(failedStepId)) {
            return List.of();
        }

        Set<String> completedStepIds = stepIds(request.completedSteps());
        List<AcademicPlanStep> planSteps = plan.getSteps();
        Set<String> usedStepIds = stepIds(planSteps);
        String recoveryStepId = nextRecoveryStepId(usedStepIds, request.replanCount());
        Set<String> remainingStepIds = remainingStepIds(planSteps, completedStepIds, failedStepId);

        List<AcademicPlanStep> replanned = new ArrayList<>();
        replanned.add(recoveryStep(recoveryStepId, failedStep, request.failedResult(), completedStepIds));

        for (AcademicPlanStep step : planSteps) {
            if (!shouldKeepRemainingStep(step, completedStepIds, failedStepId)) {
                continue;
            }
            AcademicPlanStep copy = step.copy();
            copy.setStatus(AcademicPlanLifecycleService.STATUS_NOT_STARTED);
            copy.setNote("");
            copy.setDependencies(rewriteDependencies(copy, completedStepIds, remainingStepIds,
                    failedStepId, recoveryStepId));
            replanned.add(copy);
        }
        normalizeOrder(replanned);
        return replanned;
    }

    private AcademicPlanStep recoveryStep(String recoveryStepId,
                                          AcademicPlanStep failedStep,
                                          AcademicAgentStepExecutionResult failedResult,
                                          Set<String> completedStepIds) {
        String note = failedResult == null ? "" : failedResult.note();
        String instruction = "Recover failed step " + failedStep.getStepId() + ": " + failedStep.getInstruction();
        if (StringUtils.hasText(note)) {
            instruction = instruction + ". Failure: " + note;
        }
        String assignedAgent = StringUtils.hasText(failedStep.getAssignedAgent())
                ? failedStep.getAssignedAgent()
                : DEFAULT_RECOVERY_AGENT;
        return AcademicPlanStep.builder(recoveryStepId, instruction)
                .assignedAgent(assignedAgent)
                .dependencies(completedDependencies(failedStep, completedStepIds))
                .build();
    }

    private List<String> rewriteDependencies(AcademicPlanStep step,
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

    private List<String> completedDependencies(AcademicPlanStep step, Set<String> completedStepIds) {
        return step.getDependencies().stream()
                .map(this::normalize)
                .filter(completedStepIds::contains)
                .distinct()
                .toList();
    }

    private Set<String> remainingStepIds(List<AcademicPlanStep> steps,
                                         Set<String> completedStepIds,
                                         String failedStepId) {
        LinkedHashSet<String> remainingStepIds = new LinkedHashSet<>();
        for (AcademicPlanStep step : steps) {
            if (shouldKeepRemainingStep(step, completedStepIds, failedStepId)) {
                remainingStepIds.add(step.getStepId());
            }
        }
        return remainingStepIds;
    }

    private boolean shouldKeepRemainingStep(AcademicPlanStep step,
                                            Set<String> completedStepIds,
                                            String failedStepId) {
        if (step == null || !StringUtils.hasText(step.getStepId())) {
            return false;
        }
        String stepId = step.getStepId();
        return !completedStepIds.contains(stepId) && !stepId.equals(failedStepId);
    }

    private Set<String> stepIds(List<AcademicPlanStep> steps) {
        LinkedHashSet<String> stepIds = new LinkedHashSet<>();
        if (steps == null) {
            return stepIds;
        }
        for (AcademicPlanStep step : steps) {
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

    private void normalizeOrder(List<AcademicPlanStep> steps) {
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setOrder(index + 1);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
