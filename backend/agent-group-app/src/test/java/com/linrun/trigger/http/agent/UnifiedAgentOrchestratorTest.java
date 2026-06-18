package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AcademicAgentStreamRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnifiedAgentOrchestratorTest {

    @Test
    void shouldKeepExplicitPptModeWhenFileIsAttached() {
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        AcademicAgentStreamRequest request = new AcademicAgentStreamRequest();
        request.setTaskType("ppt");
        request.setFileId("F10001");

        UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                "根据这个文件生成一份答辩 PPT",
                "ppt",
                "F10001",
                false,
                request);

        assertEquals("PPT Workflow", plan.modeSelection().getExecutionMode());
        assertEquals("ppt-workflow", plan.modeSelection().getModeFamily());
        assertEquals("ppt", plan.modeSelection().getAgentType());
        assertEquals("ppt", plan.routing().agentType());
    }
}
