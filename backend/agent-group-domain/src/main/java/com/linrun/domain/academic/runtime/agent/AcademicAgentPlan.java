package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AcademicAgentPlan {

    private String title;
    private List<AcademicPlanStep> steps;

    public AcademicAgentPlan() {
        this.steps = new ArrayList<>();
    }

    public AcademicAgentPlan(String title, List<AcademicPlanStep> steps) {
        this.title = safe(title);
        this.steps = steps == null ? new ArrayList<>() : copySteps(steps);
    }

    public static AcademicAgentPlan create(String title, List<String> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("plan steps cannot be empty");
        }
        List<AcademicPlanStep> steps = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            String instruction = instructions.get(index);
            if (!StringUtils.hasText(instruction)) {
                throw new IllegalArgumentException("plan step cannot be blank");
            }
            steps.add(AcademicPlanStep.builder("S" + (index + 1), instruction)
                    .order(index + 1)
                    .build());
        }
        return new AcademicAgentPlan(title, steps);
    }

    public AcademicAgentPlan copy() {
        return new AcademicAgentPlan(title, steps);
    }

    public Optional<AcademicPlanStep> currentStep() {
        return steps.stream()
                .filter(step -> AcademicPlanLifecycleService.STATUS_IN_PROGRESS.equals(step.getStatus()))
                .findFirst();
    }

    public int currentStepIndex() {
        for (int index = 0; index < steps.size(); index++) {
            if (AcademicPlanLifecycleService.STATUS_IN_PROGRESS.equals(steps.get(index).getStatus())) {
                return index;
            }
        }
        return -1;
    }

    public boolean allCompleted() {
        return steps.isEmpty() || steps.stream().allMatch(AcademicPlanStep::isCompleted);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = safe(title);
    }

    public List<AcademicPlanStep> getSteps() {
        return copySteps(steps);
    }

    public void setSteps(List<AcademicPlanStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : copySteps(steps);
    }

    List<AcademicPlanStep> mutableSteps() {
        return steps;
    }

    private static List<AcademicPlanStep> copySteps(List<AcademicPlanStep> steps) {
        return steps.stream()
                .map(AcademicPlanStep::copy)
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















