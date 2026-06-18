package com.linrun.domain.academic.runtime.agent;

public record AcademicAgentStepExecutionResult(boolean success, String note) {

    public static AcademicAgentStepExecutionResult success(String note) {
        return new AcademicAgentStepExecutionResult(true, AcademicAgentValues.safe(note));
    }

    public static AcademicAgentStepExecutionResult failed(String note) {
        return new AcademicAgentStepExecutionResult(false, AcademicAgentValues.safe(note));
    }
}















