package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AcademicReActExecutionService {

    public static final String STOP_REASON_FINAL_ANSWER = "final_answer";
    public static final String STOP_REASON_MAX_ROUNDS = "max_rounds_exhausted";
    public static final String STOP_REASON_EMPTY_DECISION = "empty_decision";
    public static final String STOP_REASON_MISSING_ACTION = "missing_action";
    public static final String STOP_REASON_MISSING_EXECUTOR = "missing_action_executor";

    private final int maxRounds;

    public AcademicReActExecutionService() {
        this(5);
    }

    public AcademicReActExecutionService(int maxRounds) {
        this.maxRounds = Math.max(1, maxRounds);
    }

    public AcademicReActExecutionResult execute(String runId,
                                                String userInput,
                                                AcademicReActReasoner reasoner,
                                                AcademicReActActionExecutor actionExecutor) {
        if (reasoner == null) {
            throw new IllegalArgumentException("react reasoner cannot be null");
        }
        List<AcademicReActTurn> turns = new ArrayList<>();
        for (int roundIndex = 1; roundIndex <= maxRounds; roundIndex++) {
            AcademicReActExecutionContext context = new AcademicReActExecutionContext(
                    runId, userInput, roundIndex, turns);
            AcademicReActDecision decision = reasoner.think(context);
            if (decision == null) {
                turns.add(AcademicReActTurn.blocked(roundIndex, null, "reasoner returned empty decision"));
                return result(turns, false, "", STOP_REASON_EMPTY_DECISION);
            }
            if (decision.finalAnswer()) {
                turns.add(AcademicReActTurn.finalAnswer(roundIndex, decision));
                return result(turns, true, decision.answer(), STOP_REASON_FINAL_ANSWER);
            }
            if (!decision.hasAction()) {
                turns.add(AcademicReActTurn.blocked(roundIndex, decision, "decision did not provide an action"));
                return result(turns, false, "", STOP_REASON_MISSING_ACTION);
            }
            if (actionExecutor == null) {
                turns.add(AcademicReActTurn.blocked(roundIndex, decision, "action executor is not configured"));
                return result(turns, false, "", STOP_REASON_MISSING_EXECUTOR);
            }
            AcademicReActObservation observation = actionExecutor.act(decision, context);
            turns.add(AcademicReActTurn.observed(roundIndex, decision, observation));
        }
        return result(turns, false, "", STOP_REASON_MAX_ROUNDS);
    }

    private AcademicReActExecutionResult result(List<AcademicReActTurn> turns,
                                                boolean completed,
                                                String answer,
                                                String stopReason) {
        return new AcademicReActExecutionResult(turns, completed, answer, StringUtils.hasText(stopReason)
                ? stopReason
                : STOP_REASON_MAX_ROUNDS);
    }
}
