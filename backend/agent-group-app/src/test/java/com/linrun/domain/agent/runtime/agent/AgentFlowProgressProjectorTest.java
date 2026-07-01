package com.linrun.domain.agent.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFlowProgressProjectorTest {

    private final AgentFlowProgressProjector projector = new AgentFlowProgressProjector();

    @Test
    void shouldProjectStageStartToolAdvanceAndCompletion() {
        AgentPlan plan = new AgentRunPlanFactory().build("deep", true);

        AgentFlowProgressResult start = projector.start(plan);
        AgentFlowProgressResult tool = projector.advanceToTool(plan, start.getCurrentStageIndex(), "deep_search");
        AgentFlowProgressResult done = projector.completeRemaining(plan, tool.getCurrentStageIndex());

        assertEquals(0, start.getCurrentStageIndex());
        assertEquals(AgentFlowProgress.STATUS_RUNNING, start.getEvents().getFirst().getStatus());
        assertEquals(1, tool.getCurrentStageIndex());
        assertEquals(List.of(AgentFlowProgress.STATUS_COMPLETED,
                        AgentFlowProgress.STATUS_RUNNING),
                tool.getEvents().stream().map(AgentFlowProgress::getStatus).toList());
        assertEquals(4, done.getCurrentStageIndex());
        assertEquals(3, done.getEvents().size());
        assertEquals(AgentFlowProgress.STATUS_COMPLETED, done.getEvents().getLast().getStatus());
    }

    @Test
    void shouldBlockCurrentStageWhenExecutionFails() {
        AgentPlan plan = new AgentRunPlanFactory().build("data", false);

        AgentFlowProgressResult blocked = projector.blockCurrent(plan, 2, "sql failed");

        assertEquals(2, blocked.getCurrentStageIndex());
        assertEquals(AgentFlowProgress.STATUS_BLOCKED, blocked.getEvents().getFirst().getStatus());
        assertEquals("sql failed", blocked.getEvents().getFirst().getMessage());
    }

    @Test
    void shouldMarkCurrentStageAsReplannedBeforeNewPlanStarts() {
        AgentPlan oldPlan = new AgentRunPlanFactory().build("data", false);
        AgentPlan newPlan = new AgentPlan("replanned", List.of(
                AgentPlanStep.builder("R1", "read quota ledger").order(1).build(),
                AgentPlanStep.builder("R2", "summarize compensation").order(2).dependencies(List.of("R1")).build()
        ));

        AgentFlowProgressResult replanned = projector.markReplanned(oldPlan, 1, "sql failed");
        AgentFlowProgressResult restarted = projector.start(newPlan);

        assertEquals(1, replanned.getCurrentStageIndex());
        assertEquals(AgentFlowProgress.STATUS_REPLANNED, replanned.getEvents().getFirst().getStatus());
        assertEquals("sql failed", replanned.getEvents().getFirst().getMessage());
        assertEquals(0, restarted.getCurrentStageIndex());
        assertEquals(AgentFlowProgress.STATUS_RUNNING, restarted.getEvents().getFirst().getStatus());
        assertEquals(List.of("R1"), restarted.getEvents().getFirst().getStage().stepIds());
    }
}















