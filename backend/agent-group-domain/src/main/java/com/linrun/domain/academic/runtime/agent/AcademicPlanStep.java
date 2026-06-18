package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AcademicPlanStep {

    private String stepId;
    private String instruction;
    private int order;
    private String status;
    private String note;
    private String assignedAgent;
    private List<String> dependencies;

    public AcademicPlanStep() {
        this.dependencies = new ArrayList<>();
    }

    private AcademicPlanStep(Builder builder) {
        this.stepId = safe(builder.stepId);
        this.instruction = safe(builder.instruction);
        this.order = normalizeOrder(builder.order);
        this.status = normalizeStatus(builder.status);
        this.note = safe(builder.note);
        this.assignedAgent = safe(builder.assignedAgent);
        this.dependencies = copyDependencies(builder.dependencies);
        if (!StringUtils.hasText(this.stepId)) {
            throw new IllegalArgumentException("plan step id cannot be blank");
        }
        if (!StringUtils.hasText(this.instruction)) {
            throw new IllegalArgumentException("plan step instruction cannot be blank");
        }
    }

    public static Builder builder(String stepId, String instruction) {
        return new Builder(stepId, instruction);
    }

    public AcademicPlanStep copy() {
        return builder(stepId, instruction)
                .order(order)
                .status(status)
                .note(note)
                .assignedAgent(assignedAgent)
                .dependencies(dependencies)
                .build();
    }

    public boolean isCompleted() {
        return AcademicPlanLifecycleService.STATUS_COMPLETED.equals(status);
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = safe(stepId);
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = safe(instruction);
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = normalizeOrder(order);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = normalizeStatus(status);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = safe(note);
    }

    public String getAssignedAgent() {
        return assignedAgent;
    }

    public void setAssignedAgent(String assignedAgent) {
        this.assignedAgent = safe(assignedAgent);
    }

    public List<String> getDependencies() {
        return new ArrayList<>(dependencies);
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = copyDependencies(dependencies);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeOrder(int order) {
        return Math.max(1, order);
    }

    private static String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : AcademicPlanLifecycleService.STATUS_NOT_STARTED;
    }

    private static List<String> copyDependencies(List<String> dependencies) {
        return dependencies == null ? new ArrayList<>() : new ArrayList<>(dependencies);
    }

    public static final class Builder {

        private final String stepId;
        private final String instruction;
        private int order = 1;
        private String status = AcademicPlanLifecycleService.STATUS_NOT_STARTED;
        private String note = "";
        private String assignedAgent = "";
        private List<String> dependencies = new ArrayList<>();

        private Builder(String stepId, String instruction) {
            this.stepId = stepId;
            this.instruction = instruction;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public Builder assignedAgent(String assignedAgent) {
            this.assignedAgent = assignedAgent;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = copyDependencies(dependencies);
            return this;
        }

        public AcademicPlanStep build() {
            return new AcademicPlanStep(this);
        }
    }
}















