package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.domain.academic.runtime.mode.AgentModeSelector;
import com.linrun.trigger.agent.agent.deepresearch.PlanExecuteDomainBridge;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线上 Agent 引擎路由与 domain 重规划策略的单测入口。
 */
class AgentEngineRoutingTest {

    @Test
    void shouldRouteAutoModeWithHighAccuracy() throws Exception {
        List<RoutingCase> cases = loadCases();
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();

        long correct = 0;
        for (RoutingCase routingCase : cases) {
            AcademicAgentStreamRequest request = buildRequest(routingCase);
            String fileIds = routingCase.hasAttachment() ? "F10001" : "";
            UnifiedAgentOrchestrator.OrchestrationPlan plan = orchestrator.plan(
                    routingCase.question(),
                    UnifiedAgentOrchestrator.AUTO_TASK_TYPE,
                    fileIds,
                    false,
                    request);
            AgentModeSelector.ModeSelectionResult selection = plan.modeSelection();
            String executionType = UnifiedAgentOrchestrator.resolveExecutionAgentType(
                    UnifiedAgentOrchestrator.AUTO_TASK_TYPE, plan);

            if (matchesRoutingExpectation(routingCase, selection, executionType)) {
                correct++;
            }
        }

        double accuracy = cases.isEmpty() ? 0D : (double) correct / cases.size();
        assertTrue(accuracy >= 0.9D, "auto 模式路由准确率低于 90%：" + accuracy);
    }

    @Test
    void shouldRecoverDeepTasksViaDomainReplanBridge() {
        PlanExecuteDomainBridge bridge = new PlanExecuteDomainBridge();
        List<PlanTask> plan = List.of(
                new PlanTask("S1", "收集资料", 1),
                new PlanTask("S2", "整理报告", 2));
        Map<String, TaskResult> results = Map.of(
                "S1", new TaskResult("S1", true, "done", null),
                "S2", new TaskResult("S2", false, null, "tool not found: simulated"));

        assertTrue(bridge.buildRetryTasks(plan, results, 0).isPresent());
        assertTrue(bridge.reflect(plan, results).needReplan());
    }

    private AcademicAgentStreamRequest buildRequest(RoutingCase routingCase) {
        if (!routingCase.hasAttachment() || !"image".equals(routingCase.attachmentType())) {
            return null;
        }
        AcademicAgentStreamRequest request = new AcademicAgentStreamRequest();
        request.setImageUrl("https://example.com/demo.png");
        return request;
    }

    private boolean matchesRoutingExpectation(RoutingCase routingCase,
                                              AgentModeSelector.ModeSelectionResult selection,
                                              String executionType) {
        if (selection == null) {
            return false;
        }
        if (!routingCase.expectedExecutionMode().equals(selection.getExecutionMode())) {
            return false;
        }
        if (!routingCase.expectedAgentType().equals(selection.getAgentType())) {
            return false;
        }
        return expectedRoutedAgentType(routingCase.expectedAgentType()).equals(executionType);
    }

    private String expectedRoutedAgentType(String agentType) {
        return switch (agentType) {
            case "search" -> "chat";
            case "skill" -> "manual-skills";
            default -> agentType;
        };
    }

    private List<RoutingCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-engine/routing-cases.json")) {
            assertNotNull(input, "routing-cases.json 不存在");
            return new ObjectMapper().readValue(input, new TypeReference<List<RoutingCase>>() {
            });
        }
    }

    private record RoutingCase(String caseId,
                               String question,
                               String expectedAgentType,
                               String expectedExecutionMode,
                               String attachmentType) {

        boolean hasAttachment() {
            return attachmentType != null && !attachmentType.isBlank();
        }
    }
}
