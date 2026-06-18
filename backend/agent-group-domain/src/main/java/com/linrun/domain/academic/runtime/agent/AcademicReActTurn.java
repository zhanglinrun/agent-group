package com.linrun.domain.academic.runtime.agent;

import java.util.Map;

public record AcademicReActTurn(int roundIndex,
                                String thought,
                                String actionName,
                                Map<String, Object> actionArguments,
                                String status,
                                String observation,
                                Map<String, Object> observationMetadata,
                                String answer) {

    public static final String STATUS_OBSERVED = "observed";
    public static final String STATUS_FINAL = "final";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_BLOCKED = "blocked";

    public AcademicReActTurn {
        roundIndex = Math.max(1, roundIndex);
        thought = AcademicAgentValues.safe(thought);
        actionName = AcademicAgentValues.safe(actionName);
        actionArguments = AcademicAgentValues.copyMap(actionArguments);
        status = AcademicAgentValues.safe(status);
        observation = AcademicAgentValues.safe(observation);
        observationMetadata = AcademicAgentValues.copyMap(observationMetadata);
        answer = AcademicAgentValues.safe(answer);
    }

    public static AcademicReActTurn observed(int roundIndex,
                                             AcademicReActDecision decision,
                                             AcademicReActObservation observation) {
        AcademicReActObservation safeObservation = observation == null
                ? AcademicReActObservation.failed("action returned empty observation")
                : observation;
        return new AcademicReActTurn(
                roundIndex,
                decision == null ? "" : decision.thought(),
                decision == null ? "" : decision.actionName(),
                decision == null ? Map.of() : decision.actionArguments(),
                safeObservation.success() ? STATUS_OBSERVED : STATUS_FAILED,
                safeObservation.content(),
                safeObservation.metadata(),
                "");
    }

    public static AcademicReActTurn finalAnswer(int roundIndex, AcademicReActDecision decision) {
        return new AcademicReActTurn(
                roundIndex,
                decision == null ? "" : decision.thought(),
                "",
                Map.of(),
                STATUS_FINAL,
                "",
                Map.of(),
                decision == null ? "" : decision.answer());
    }

    public static AcademicReActTurn blocked(int roundIndex,
                                            AcademicReActDecision decision,
                                            String observation) {
        return new AcademicReActTurn(
                roundIndex,
                decision == null ? "" : decision.thought(),
                decision == null ? "" : decision.actionName(),
                decision == null ? Map.of() : decision.actionArguments(),
                STATUS_BLOCKED,
                observation,
                Map.of(),
                "");
    }

}















