package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AgentPlan {

    private String title;
    private List<AgentPlanStep> steps;

    public AgentPlan() {
        this.steps = new ArrayList<>();
    }

    public AgentPlan(String title, List<AgentPlanStep> steps) {
        this.title = safe(title);
        this.steps = new ArrayList<>(AgentPlanSteps.copyAll(steps));
    }

    public static AgentPlan create(String title, List<String> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("plan steps cannot be empty");
        }
        List<AgentPlanStep> steps = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            String instruction = instructions.get(index);
            if (!StringUtils.hasText(instruction)) {
                throw new IllegalArgumentException("plan step cannot be blank");
            }
            steps.add(AgentPlanStep.builder("S" + (index + 1), instruction)
                    .order(index + 1)
                    .build());
        }
        return new AgentPlan(title, steps);
    }

    public AgentPlan copy() {
        return new AgentPlan(title, steps);
    }

    public Optional<AgentPlanStep> currentStep() {
        return steps.stream()
                .filter(step -> AgentPlanLifecycleService.STATUS_IN_PROGRESS.equals(step.getStatus()))
                .findFirst();
    }

    public int currentStepIndex() {
        for (int index = 0; index < steps.size(); index++) {
            if (AgentPlanLifecycleService.STATUS_IN_PROGRESS.equals(steps.get(index).getStatus())) {
                return index;
            }
        }
        return -1;
    }

    public boolean allCompleted() {
        return steps.isEmpty() || steps.stream().allMatch(AgentPlanStep::isCompleted);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = safe(title);
    }

    public List<AgentPlanStep> getSteps() {
        return AgentPlanSteps.copyAll(steps);
    }

    public void setSteps(List<AgentPlanStep> steps) {
        this.steps = new ArrayList<>(AgentPlanSteps.copyAll(steps));
    }

    List<AgentPlanStep> mutableSteps() {
        return steps;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















