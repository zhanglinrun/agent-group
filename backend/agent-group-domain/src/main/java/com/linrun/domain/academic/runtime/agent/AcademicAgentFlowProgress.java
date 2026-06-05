package com.linrun.domain.academic.runtime.agent;

public class AcademicAgentFlowProgress {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_REPLANNED = "REPLANNED";

    private final AcademicAgentFlowStage stage;
    private final String status;
    private final String message;

    public AcademicAgentFlowProgress(AcademicAgentFlowStage stage, String status, String message) {
        this.stage = stage;
        this.status = status == null ? "" : status.trim();
        this.message = message == null ? "" : message.trim();
    }

    public AcademicAgentFlowStage getStage() {
        return stage;
    }

    public int getStageIndex() {
        return stage == null ? -1 : stage.getStageIndex();
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
