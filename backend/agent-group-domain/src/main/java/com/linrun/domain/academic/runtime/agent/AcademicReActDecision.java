package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AcademicReActDecision(String thought,
                                    String actionName,
                                    Map<String, Object> actionArguments,
                                    boolean finalAnswer,
                                    String answer) {

    public AcademicReActDecision {
        thought = safe(thought);
        actionName = safe(actionName);
        actionArguments = copyMap(actionArguments);
        answer = safe(answer);
    }

    public static AcademicReActDecision action(String thought,
                                               String actionName,
                                               Map<String, Object> actionArguments) {
        return new AcademicReActDecision(thought, actionName, actionArguments, false, "");
    }

    public static AcademicReActDecision finalAnswer(String thought, String answer) {
        return new AcademicReActDecision(thought, "", Map.of(), true, answer);
    }

    public boolean hasAction() {
        return StringUtils.hasText(actionName);
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















