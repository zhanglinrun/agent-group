package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AcademicAgentStreamRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("ppt", UnifiedAgentOrchestrator.resolveExecutionAgentType("ppt", plan));
    }

    @Test
    void shouldRouteAutoModeToDeepResearchForComplexQuestion() {
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        String question = "请深入研究一下人工智能在医疗领域的应用现状和未来发展趋势";

        UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                question,
                UnifiedAgentOrchestrator.AUTO_TASK_TYPE,
                "",
                false,
                new AcademicAgentStreamRequest());

        assertEquals("deep", plan.routing().agentType());
        assertEquals("deep", UnifiedAgentOrchestrator.resolveExecutionAgentType(
                UnifiedAgentOrchestrator.AUTO_TASK_TYPE, plan));
    }

    @Test
    void shouldKeepExplicitChatModeEvenWhenQuestionLooksLikeDeepResearch() {
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        String question = "请深入研究一下人工智能在医疗领域的应用现状和未来发展趋势";

        UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                question,
                "chat",
                "",
                false,
                new AcademicAgentStreamRequest());

        assertEquals("deep", plan.modeSelection().getAgentType());
        assertEquals("chat", UnifiedAgentOrchestrator.resolveExecutionAgentType("chat", plan));
    }

    @Test
    void shouldMarkExecutionAppliedPayloadForAutoMode() {
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                "帮我做一个关于机器学习的PPT",
                UnifiedAgentOrchestrator.AUTO_TASK_TYPE,
                "",
                false,
                new AcademicAgentStreamRequest());

        var data = UnifiedAgentOrchestrator.executionAppliedData(
                "RUN-1",
                UnifiedAgentOrchestrator.AUTO_TASK_TYPE,
                plan.routing().agentType(),
                plan);

        assertEquals("auto", data.get("requestedTaskType"));
        assertEquals("ppt", data.get("executionAgentType"));
        assertTrue((Boolean) data.get("autoRouted"));
        assertEquals("PPT Workflow", data.get("executionMode"));
    }

    @Test
    void shouldNotMarkAutoRoutedForExplicitDeepMode() {
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                "介绍一下 Spring Boot",
                "deep",
                "",
                false,
                new AcademicAgentStreamRequest());

        var data = UnifiedAgentOrchestrator.executionAppliedData(
                "RUN-2",
                "deep",
                UnifiedAgentOrchestrator.resolveExecutionAgentType("deep", plan),
                plan);

        assertEquals("deep", data.get("requestedTaskType"));
        assertEquals("deep", data.get("executionAgentType"));
        assertFalse((Boolean) data.get("autoRouted"));
    }
}
