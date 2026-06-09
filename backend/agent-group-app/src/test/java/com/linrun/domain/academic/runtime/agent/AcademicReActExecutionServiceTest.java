package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicReActExecutionServiceTest {

    @Test
    void shouldRunThoughtActionObservationUntilFinalAnswer() {
        AcademicReActExecutionService service = new AcademicReActExecutionService(3);
        AtomicInteger actionCount = new AtomicInteger();

        AcademicReActExecutionResult result = service.execute(
                "RUN1001",
                "find papers about retrieval augmented generation",
                context -> {
                    if (context.previousTurns().isEmpty()) {
                        return AcademicReActDecision.action(
                                "Need literature evidence before answering.",
                                "literature_search",
                                Map.of("query", "retrieval augmented generation"));
                    }
                    AcademicReActTurn lastTurn = context.previousTurns().getLast();
                    assertEquals("Found 3 high relevance papers", lastTurn.observation());
                    return AcademicReActDecision.finalAnswer(
                            "RAG combines retrieval with generation.",
                            "RAG combines retrieval with generation and should cite retrieved papers.");
                },
                (decision, context) -> {
                    actionCount.incrementAndGet();
                    assertEquals("literature_search", decision.actionName());
                    assertEquals("retrieval augmented generation", decision.actionArguments().get("query"));
                    return AcademicReActObservation.success(
                            "Found 3 high relevance papers",
                            Map.of("hitCount", 3));
                });

        assertTrue(result.completed());
        assertEquals("RAG combines retrieval with generation and should cite retrieved papers.", result.answer());
        assertEquals(AcademicReActExecutionService.STOP_REASON_FINAL_ANSWER, result.stopReason());
        assertEquals(1, actionCount.get());
        assertEquals(List.of(
                        AcademicReActTurn.STATUS_OBSERVED,
                        AcademicReActTurn.STATUS_FINAL),
                result.turns().stream().map(AcademicReActTurn::status).toList());
        assertEquals(3, result.turns().getFirst().observationMetadata().get("hitCount"));
    }

    @Test
    void shouldStopWhenMaxRoundsAreExhausted() {
        AcademicReActExecutionService service = new AcademicReActExecutionService(2);

        AcademicReActExecutionResult result = service.execute(
                "RUN1002",
                "keep checking",
                context -> AcademicReActDecision.action(
                        "Need one more check.",
                        "data_analysis",
                        Map.of("round", context.roundIndex())),
                (decision, context) -> AcademicReActObservation.success("round " + context.roundIndex()));

        assertFalse(result.completed());
        assertEquals(AcademicReActExecutionService.STOP_REASON_MAX_ROUNDS, result.stopReason());
        assertEquals(2, result.turns().size());
        assertEquals(List.of("round 1", "round 2"),
                result.turns().stream().map(AcademicReActTurn::observation).toList());
    }

    @Test
    void shouldBlockWhenDecisionDoesNotProvideAction() {
        AcademicReActExecutionService service = new AcademicReActExecutionService(2);

        AcademicReActExecutionResult result = service.execute(
                "RUN1003",
                "missing action",
                context -> new AcademicReActDecision("Need a tool but forgot action.", "", Map.of(), false, ""),
                null);

        assertFalse(result.completed());
        assertEquals(AcademicReActExecutionService.STOP_REASON_MISSING_ACTION, result.stopReason());
        assertEquals(AcademicReActTurn.STATUS_BLOCKED, result.turns().getFirst().status());
    }
}















