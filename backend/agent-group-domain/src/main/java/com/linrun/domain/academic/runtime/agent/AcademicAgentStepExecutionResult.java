package com.linrun.domain.academic.runtime.agent;

public record AcademicAgentStepExecutionResult(boolean success, String note) {

    public static AcademicAgentStepExecutionResult success(String note) {
        return new AcademicAgentStepExecutionResult(true, safe(note));
    }

    public static AcademicAgentStepExecutionResult failed(String note) {
        return new AcademicAgentStepExecutionResult(false, safe(note));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
