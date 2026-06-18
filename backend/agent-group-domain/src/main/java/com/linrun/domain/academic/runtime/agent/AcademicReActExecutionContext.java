package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public record AcademicReActExecutionContext(String runId,
                                            String userInput,
                                            int roundIndex,
                                            List<AcademicReActTurn> previousTurns) {

    public AcademicReActExecutionContext {
        runId = AcademicAgentValues.safe(runId);
        userInput = AcademicAgentValues.safe(userInput);
        roundIndex = Math.max(1, roundIndex);
        previousTurns = previousTurns == null ? List.of() : List.copyOf(previousTurns);
    }
}















