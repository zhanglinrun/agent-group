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
                "check order O1001",
                context -> {
                    if (context.previousTurns().isEmpty()) {
                        return AcademicReActDecision.action(
                                "Need backend order status before answering.",
                                "order_status",
                                Map.of("orderId", "O1001"));
                    }
                    AcademicReActTurn lastTurn = context.previousTurns().getLast();
                    assertEquals("PAY_SUCCESS waiting group settlement", lastTurn.observation());
                    return AcademicReActDecision.finalAnswer(
                            "Payment is not enough for quota grant.",
                            "Do not grant quota before GROUP_SETTLED.");
                },
                (decision, context) -> {
                    actionCount.incrementAndGet();
                    assertEquals("order_status", decision.actionName());
                    assertEquals("O1001", decision.actionArguments().get("orderId"));
                    return AcademicReActObservation.success(
                            "PAY_SUCCESS waiting group settlement",
                            Map.of("orderStatus", "PAY_SUCCESS"));
                });

        assertTrue(result.completed());
        assertEquals("Do not grant quota before GROUP_SETTLED.", result.answer());
        assertEquals(AcademicReActExecutionService.STOP_REASON_FINAL_ANSWER, result.stopReason());
        assertEquals(1, actionCount.get());
        assertEquals(List.of(
                        AcademicReActTurn.STATUS_OBSERVED,
                        AcademicReActTurn.STATUS_FINAL),
                result.turns().stream().map(AcademicReActTurn::status).toList());
        assertEquals("PAY_SUCCESS", result.turns().getFirst().observationMetadata().get("orderStatus"));
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
