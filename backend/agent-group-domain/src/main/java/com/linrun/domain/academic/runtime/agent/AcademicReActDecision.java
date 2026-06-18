package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.Map;

public record AcademicReActDecision(String thought,
                                    String actionName,
                                    Map<String, Object> actionArguments,
                                    boolean finalAnswer,
                                    String answer) {

    public AcademicReActDecision {
        thought = AcademicAgentValues.safe(thought);
        actionName = AcademicAgentValues.safe(actionName);
        actionArguments = AcademicAgentValues.copyMap(actionArguments);
        answer = AcademicAgentValues.safe(answer);
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

}















