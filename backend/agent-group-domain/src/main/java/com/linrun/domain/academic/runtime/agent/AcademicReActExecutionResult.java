package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public record AcademicReActExecutionResult(List<AcademicReActTurn> turns,
                                           boolean completed,
                                           String answer,
                                           String stopReason) {

    public AcademicReActExecutionResult {
        turns = turns == null ? List.of() : List.copyOf(turns);
        answer = safe(answer);
        stopReason = safe(stopReason);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
