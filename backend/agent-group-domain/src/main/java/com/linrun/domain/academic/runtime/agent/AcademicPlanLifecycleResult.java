package com.linrun.domain.academic.runtime.agent;

public class AcademicPlanLifecycleResult {

    private final AcademicAgentPlan plan;
    private final AcademicPlanStep currentStep;
    private final int currentStepIndex;
    private final boolean autoAdvanced;
    private final boolean autoFinished;

    public AcademicPlanLifecycleResult(AcademicAgentPlan plan,
                                       AcademicPlanStep currentStep,
                                       int currentStepIndex,
                                       boolean autoAdvanced,
                                       boolean autoFinished) {
        this.plan = plan == null ? null : plan.copy();
        this.currentStep = currentStep == null ? null : currentStep.copy();
        this.currentStepIndex = currentStepIndex;
        this.autoAdvanced = autoAdvanced;
        this.autoFinished = autoFinished;
    }

    public AcademicAgentPlan getPlan() {
        return plan == null ? null : plan.copy();
    }

    public AcademicPlanStep getCurrentStep() {
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















