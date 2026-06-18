package com.linrun.domain.academic.runtime.agent;

import java.util.List;

public record AcademicReActExecutionResult(List<AcademicReActTurn> turns,
                                           boolean completed,
                                           String answer,
                                           String stopReason) {

    public AcademicReActExecutionResult {
        turns = turns == null ? List.of() : List.copyOf(turns);
        answer = AcademicAgentValues.safe(answer);
        stopReason = AcademicAgentValues.safe(stopReason);
    }
}















