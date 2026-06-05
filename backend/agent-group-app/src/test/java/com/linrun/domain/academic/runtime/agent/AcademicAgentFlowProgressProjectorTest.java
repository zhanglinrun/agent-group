package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicAgentFlowProgressProjectorTest {

    private final AcademicAgentFlowProgressProjector projector = new AcademicAgentFlowProgressProjector();

    @Test
    void shouldProjectStageStartToolAdvanceAndCompletion() {
        AcademicAgentPlan plan = new AcademicAgentRunPlanFactory().build("deep", true);

        AcademicAgentFlowProgressResult start = projector.start(plan);
        AcademicAgentFlowProgressResult tool = projector.advanceToTool(plan, start.getCurrentStageIndex(), "deep_search");
        AcademicAgentFlowProgressResult done = projector.completeRemaining(plan, tool.getCurrentStageIndex());

        assertEquals(0, start.getCurrentStageIndex());
        assertEquals(AcademicAgentFlowProgress.STATUS_RUNNING, start.getEvents().getFirst().getStatus());
        assertEquals(1, tool.getCurrentStageIndex());
        assertEquals(List.of(AcademicAgentFlowProgress.STATUS_COMPLETED,
                        AcademicAgentFlowProgress.STATUS_RUNNING),
                tool.getEvents().stream().map(AcademicAgentFlowProgress::getStatus).toList());
        assertEquals(4, done.getCurrentStageIndex());
        assertEquals(3, done.getEvents().size());
        assertEquals(AcademicAgentFlowProgress.STATUS_COMPLETED, done.getEvents().getLast().getStatus());
    }

    @Test
    void shouldBlockCurrentStageWhenExecutionFails() {
        AcademicAgentPlan plan = new AcademicAgentRunPlanFactory().build("data", false);

        AcademicAgentFlowProgressResult blocked = projector.blockCurrent(plan, 2, "sql failed");

        assertEquals(2, blocked.getCurrentStageIndex());
        assertEquals(AcademicAgentFlowProgress.STATUS_BLOCKED, blocked.getEvents().getFirst().getStatus());
        assertEquals("sql failed", blocked.getEvents().getFirst().getMessage());
    }

    @Test
    void shouldMarkCurrentStageAsReplannedBeforeNewPlanStarts() {
        AcademicAgentPlan oldPlan = new AcademicAgentRunPlanFactory().build("data", false);
        AcademicAgentPlan newPlan = new AcademicAgentPlan("replanned", List.of(
                AcademicPlanStep.builder("R1", "read quota ledger").order(1).build(),
                AcademicPlanStep.builder("R2", "summarize compensation").order(2).dependencies(List.of("R1")).build()
        ));

        AcademicAgentFlowProgressResult replanned = projector.markReplanned(oldPlan, 1, "sql failed");
        AcademicAgentFlowProgressResult restarted = projector.start(newPlan);

        assertEquals(1, replanned.getCurrentStageIndex());
        assertEquals(AcademicAgentFlowProgress.STATUS_REPLANNED, replanned.getEvents().getFirst().getStatus());
        assertEquals("sql failed", replanned.getEvents().getFirst().getMessage());
        assertEquals(0, restarted.getCurrentStageIndex());
        assertEquals(AcademicAgentFlowProgress.STATUS_RUNNING, restarted.getEvents().getFirst().getStatus());
        assertEquals(List.of("R1"), restarted.getEvents().getFirst().getStage().stepIds());
    }
}
