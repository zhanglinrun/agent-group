package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentFlowProjector {

    public List<AgentFlowStage> buildRemainingStages(AgentPlan plan) {
        if (plan == null || plan.getSteps().isEmpty()) {
            return List.of();
        }
        List<AgentPlanStep> allSteps = plan.getSteps();
        Map<String, AgentPlanStep> byId = new HashMap<>();
        for (AgentPlanStep step : allSteps) {
            byId.put(step.getStepId(), step);
        }

        Set<String> satisfied = new HashSet<>();
        Set<String> pending = new LinkedHashSet<>();
        for (AgentPlanStep step : allSteps) {
            validateDependencies(step, byId);
            if (step.isCompleted()) {
                satisfied.add(step.getStepId());
            } else {
                pending.add(step.getStepId());
            }
        }

        List<AgentFlowStage> stages = new ArrayList<>();
        int stageIndex = 0;
        while (!pending.isEmpty()) {
            List<AgentPlanStep> dependencyReady = pending.stream()
                    .map(byId::get)
                    .filter(step -> dependenciesSatisfied(step, satisfied))
                    .sorted(Comparator.comparingInt(AgentPlanStep::getOrder)
                            .thenComparing(AgentPlanStep::getStepId))
                    .toList();
            if (dependencyReady.isEmpty()) {
                throw new IllegalStateException("plan flow has cyclic dependencies");
            }
            int nextOrder = dependencyReady.getFirst().getOrder();
            List<AgentPlanStep> ready = dependencyReady.stream()
                    .filter(step -> step.getOrder() == nextOrder)
                    .toList();
            if (ready.isEmpty()) {
                throw new IllegalStateException("plan flow has cyclic dependencies");
            }
            stages.add(new AgentFlowStage(stageIndex++, ready));
            for (AgentPlanStep step : ready) {
                pending.remove(step.getStepId());
                satisfied.add(step.getStepId());
            }
        }
        return stages;
    }

    public List<AgentPlanStep> nextExecutableSteps(AgentPlan plan) {
        List<AgentFlowStage> stages = buildRemainingStages(plan);
        if (stages.isEmpty()) {
            return List.of();
        }
        return stages.getFirst().getSteps();
    }

    private void validateDependencies(AgentPlanStep step, Map<String, AgentPlanStep> byId) {
        for (String dependency : step.getDependencies()) {
            if (!StringUtils.hasText(dependency)) {
                continue;
            }
            if (!byId.containsKey(dependency.trim())) {
                throw new IllegalArgumentException("unknown plan dependency: " + dependency);
            }
        }
    }

    private boolean dependenciesSatisfied(AgentPlanStep step, Set<String> satisfied) {
        for (String dependency : step.getDependencies()) {
            if (StringUtils.hasText(dependency) && !satisfied.contains(dependency.trim())) {
                return false;
            }
        }
        return true;
    }
}















