package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AcademicAgentFlowProjector {

    public List<AcademicAgentFlowStage> buildRemainingStages(AcademicAgentPlan plan) {
        if (plan == null || plan.getSteps().isEmpty()) {
            return List.of();
        }
        List<AcademicPlanStep> allSteps = plan.getSteps();
        Map<String, AcademicPlanStep> byId = new HashMap<>();
        for (AcademicPlanStep step : allSteps) {
            byId.put(step.getStepId(), step);
        }

        Set<String> satisfied = new HashSet<>();
        Set<String> pending = new LinkedHashSet<>();
        for (AcademicPlanStep step : allSteps) {
            validateDependencies(step, byId);
            if (step.isCompleted()) {
                satisfied.add(step.getStepId());
            } else {
                pending.add(step.getStepId());
            }
        }

        List<AcademicAgentFlowStage> stages = new ArrayList<>();
        int stageIndex = 0;
        while (!pending.isEmpty()) {
            List<AcademicPlanStep> dependencyReady = pending.stream()
                    .map(byId::get)
                    .filter(step -> dependenciesSatisfied(step, satisfied))
                    .sorted(Comparator.comparingInt(AcademicPlanStep::getOrder)
                            .thenComparing(AcademicPlanStep::getStepId))
                    .toList();
            if (dependencyReady.isEmpty()) {
                throw new IllegalStateException("plan flow has cyclic dependencies");
            }
            int nextOrder = dependencyReady.getFirst().getOrder();
            List<AcademicPlanStep> ready = dependencyReady.stream()
                    .filter(step -> step.getOrder() == nextOrder)
                    .toList();
            if (ready.isEmpty()) {
                throw new IllegalStateException("plan flow has cyclic dependencies");
            }
            stages.add(new AcademicAgentFlowStage(stageIndex++, ready));
            for (AcademicPlanStep step : ready) {
                pending.remove(step.getStepId());
                satisfied.add(step.getStepId());
            }
        }
        return stages;
    }

    public List<AcademicPlanStep> nextExecutableSteps(AcademicAgentPlan plan) {
        List<AcademicAgentFlowStage> stages = buildRemainingStages(plan);
        if (stages.isEmpty()) {
            return List.of();
        }
        return stages.getFirst().getSteps();
    }

    private void validateDependencies(AcademicPlanStep step, Map<String, AcademicPlanStep> byId) {
        for (String dependency : step.getDependencies()) {
            if (!StringUtils.hasText(dependency)) {
                continue;
            }
            if (!byId.containsKey(dependency.trim())) {
                throw new IllegalArgumentException("unknown plan dependency: " + dependency);
            }
        }
    }

    private boolean dependenciesSatisfied(AcademicPlanStep step, Set<String> satisfied) {
        for (String dependency : step.getDependencies()) {
            if (StringUtils.hasText(dependency) && !satisfied.contains(dependency.trim())) {
                return false;
            }
        }
        return true;
    }
}
