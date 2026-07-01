package com.linrun.domain.agent.runtime.agent;

public class AgentPlanLifecycleResult {

    private final AgentPlan plan;
    private final AgentPlanStep currentStep;
    private final int currentStepIndex;
    private final boolean autoAdvanced;
    private final boolean autoFinished;

    public AgentPlanLifecycleResult(AgentPlan plan,
                                       AgentPlanStep currentStep,
                                       int currentStepIndex,
                                       boolean autoAdvanced,
                                       boolean autoFinished) {
        this.plan = plan == null ? null : plan.copy();
        this.currentStep = currentStep == null ? null : currentStep.copy();
        this.currentStepIndex = currentStepIndex;
        this.autoAdvanced = autoAdvanced;
        this.autoFinished = autoFinished;
    }

    public AgentPlan getPlan() {
        return plan == null ? null : plan.copy();
    }

    public AgentPlanStep getCurrentStep() {
        return currentStep == null ? null : currentStep.copy();
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public boolean isAutoAdvanced() {
        return autoAdvanced;
    }

    public boolean isAutoFinished() {
        return autoFinished;
    }
}















