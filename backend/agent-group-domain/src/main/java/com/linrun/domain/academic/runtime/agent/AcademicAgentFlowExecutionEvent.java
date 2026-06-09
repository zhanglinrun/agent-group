package com.linrun.domain.academic.runtime.agent;

public class AcademicAgentFlowExecutionEvent {

    public static final String TYPE_STAGE_STARTED = "stage_started";
    public static final String TYPE_STEP_STARTED = "step_started";
    public static final String TYPE_STEP_COMPLETED = "step_completed";
    public static final String TYPE_STEP_BLOCKED = "step_blocked";
    public static final String TYPE_REPLANNED = "replanned";

    private final String eventType;
    private final int stageIndex;
    private final String stepId;
    private final String instruction;
    private final String note;

    public AcademicAgentFlowExecutionEvent(String eventType,
                                           int stageIndex,
                                           String stepId,
                                           String instruction,
                                           String note) {
        this.eventType = safe(eventType);
        this.stageIndex = stageIndex;
        this.stepId = safe(stepId);
        this.instruction = safe(instruction);
        this.note = safe(note);
    }

    public String getEventType() {
        return eventType;
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public String getStepId() {
        return stepId;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getNote() {
        return note;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















