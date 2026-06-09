package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        thought = safe(thought);
        actionName = safe(actionName);
        actionArguments = copyMap(actionArguments);
        status = safe(status);
        observation = safe(observation);
        observationMetadata = copyMap(observationMetadata);
        answer = safe(answer);
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

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (StringUtils.hasText(key)) {
                result.put(key.trim(), value == null ? "" : value);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















