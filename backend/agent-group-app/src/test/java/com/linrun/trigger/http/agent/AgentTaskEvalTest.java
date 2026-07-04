package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentStreamRequest;
import com.linrun.domain.agent.runtime.mode.AgentModeSelector;
import com.linrun.trigger.agent.agent.deepresearch.PlanExecuteDomainBridge;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 任务评测：路由 + domain 重规划/反思（规则判定，不调用 LLM）。
 */
class AgentTaskEvalTest {

    @Test
    void taskRoutingAccuracyOnExtendedCases() throws Exception {
        List<TaskCase> cases = loadCases();
        UnifiedAgentOrchestrator orchestrator = new UnifiedAgentOrchestrator();
        long correct = 0;
        for (TaskCase c : cases) {
            AgentStreamRequest request = buildRequest(c);
            String fileIds = c.hasAttachment() ? "F10001" : "";
            var plan = orchestrator.plan(
                    c.question(),
                    UnifiedAgentOrchestrator.AUTO_TASK_TYPE,
                    fileIds,
                    false,
                    request);
            var selection = plan.modeSelection();
            String executionType = UnifiedAgentOrchestrator.resolveExecutionAgentType(
                    UnifiedAgentOrchestrator.AUTO_TASK_TYPE, plan);
            if (matchesExpectation(c, selection, executionType)) {
                correct++;
            }
        }
        double rate = (double) correct / cases.size();
        assertTrue(rate >= 0.85, "扩展任务路由准确率=" + rate);
    }

    @Test
    void domainReplanAndReflectMetrics() {
        PlanExecuteDomainBridge bridge = new PlanExecuteDomainBridge();
        List<PlanTask> plan = List.of(
                new PlanTask("S1", "收集", 1),
                new PlanTask("S2", "分析", 2),
                new PlanTask("S3", "输出", 3));
        Map<String, TaskResult> failLast = Map.of(
                "S1", new TaskResult("S1", true, "ok", null),
                "S2", new TaskResult("S2", true, "ok", null),
                "S3", new TaskResult("S3", false, null, "timeout"));
        Map<String, TaskResult> toolMissing = Map.of(
                "S1", new TaskResult("S1", false, null, "tool not found: x"));

        int replanTriggered = 0;
        int reflectReplan = 0;
        if (bridge.buildRetryTasks(plan, failLast, 0).isPresent()) {
            replanTriggered++;
        }
        if (bridge.reflect(plan, failLast).needReplan()) {
            reflectReplan++;
        }
        if (bridge.buildRetryTasks(plan, toolMissing, 0).isPresent()) {
            replanTriggered++;
        }
        assertTrue(replanTriggered >= 2);
        assertTrue(reflectReplan >= 1);
    }

    @Test
    void combinedRoutingCaseCount() throws Exception {
        List<TaskCase> extended = loadCases();
        assertTrue(23 + extended.size() >= 38, "路由+任务评测用例总数应 ≥38");
    }

    private boolean matchesExpectation(TaskCase c,
                                       AgentModeSelector.ModeSelectionResult selection,
                                       String executionType) {
        if (selection == null) {
            return false;
        }
        if (!c.expectedExecutionMode().equals(selection.getExecutionMode())) {
            return false;
        }
        if (!c.expectedAgentType().equals(selection.getAgentType())) {
            return false;
        }
        return expectedRoutedAgentType(c.expectedAgentType()).equals(executionType);
    }

    private String expectedRoutedAgentType(String agentType) {
        return switch (agentType) {
            case "search" -> "chat";
            case "skill" -> "manual-skills";
            default -> agentType;
        };
    }

    private AgentStreamRequest buildRequest(TaskCase c) {
        if (!c.hasAttachment()) {
            return null;
        }
        if ("image".equals(c.attachmentType())) {
            AgentStreamRequest r = new AgentStreamRequest();
            r.setImageUrl("https://example.com/x.png");
            return r;
        }
        return null;
    }

    private List<TaskCase> loadCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/agent-eval/agent-task-cases.json")) {
            assertNotNull(in);
            return mapper.readValue(in, new TypeReference<>() {});
        }
    }

    private record TaskCase(
            String taskId,
            String question,
            String expectedAgentType,
            String expectedExecutionMode,
            String attachmentType) {

        boolean hasAttachment() {
            return attachmentType != null && !attachmentType.isBlank();
        }
    }
}
