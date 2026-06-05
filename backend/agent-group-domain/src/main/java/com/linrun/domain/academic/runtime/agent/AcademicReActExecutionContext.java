package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public record AcademicReActExecutionContext(String runId,
                                            String userInput,
                                            int roundIndex,
                                            List<AcademicReActTurn> previousTurns) {

    public AcademicReActExecutionContext {
        runId = safe(runId);
        userInput = safe(userInput);
        roundIndex = Math.max(1, roundIndex);
        previousTurns = previousTurns == null ? List.of() : List.copyOf(previousTurns);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
