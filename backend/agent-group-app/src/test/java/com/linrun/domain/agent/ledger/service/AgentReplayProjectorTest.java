package com.linrun.domain.agent.ledger.service;

import com.linrun.api.dto.AgentReplayResponse;
import com.linrun.api.dto.QuotaStreamEvent;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReplayProjectorTest {

    private final AgentReplayProjector projector = new AgentReplayProjector();

    @Test
    void shouldProjectStructuredToolResultIntoReplayEvents() {
        AgentRun run = new AgentRun();
        run.setRunId("RUN1001");
        run.setSessionId("S1001");
        run.setRequestId("REQ1001");
        run.setTaskType("deep");
        run.setStatus(AgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(10L);

        AgentToolInvocation toolInvocation = new AgentToolInvocation();
        toolInvocation.setInvocationId("TOOL1001");
        toolInvocation.setToolCallId("CALL1001");
        toolInvocation.setToolName(AgentToolOutputNames.REPORT_TOOL);
        toolInvocation.setStatus(AgentRun.STATUS_SUCCESS);
        toolInvocation.setResultSummary("报告已生成");
        toolInvocation.setResultJson("""
                {
                  "toolName": "report_tool",
                  "summary": "报告已生成",
                  "fileRefs": [{"artifactId": "A2001", "fileName": "report.md"}]
                }
                """);

        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactId("A2001");
        artifact.setToolInvocationId("TOOL1001");
        artifact.setTitle("报告");
        artifact.setContent("report.md");

        AgentReplayResponse response = projector.project(run, List.of(), List.of(toolInvocation), List.of(artifact));

        QuotaStreamEvent<Map<String, Object>> toolResult = response.getEvents().stream()
                .filter(event -> "tool_result".equals(event.getEvent()))
                .findFirst()
                .orElseThrow();
        assertEquals("CALL1001", toolResult.getData().get("toolCallId"));
        assertEquals(1, toolResult.getData().get("artifactCount"));
        assertEquals("file", toolResult.getData().get("resultKind"));
        assertFalse(((Map<?, ?>) toolResult.getData().get("structuredOutput")).isEmpty());
    }

    @Test
    void shouldMarkReplayResultKindForRichRuntimeTools() {
        AgentRun run = new AgentRun();
        run.setRunId("RUN4001");
        run.setSessionId("S4001");
        run.setRequestId("REQ4001");
        run.setTaskType("deep");
        run.setStatus(AgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());

        AgentReplayResponse response = projector.project(run, List.of(), List.of(
                tool("TOOL4001", AgentToolOutputNames.CODE_INTERPRETER, "generated code"),
                tool("TOOL4002", AgentToolOutputNames.DATA_ANALYSIS, "rows analyzed"),
                tool("TOOL4003", AgentToolOutputNames.IMAGE_GENERATION, "image generated"),
                tool("TOOL4004", AgentToolOutputNames.WEB_FETCH, "page fetched"),
                tool("TOOL4005", AgentToolOutputNames.MULTIMODAL_AGENT, "image understood"),
                tool("TOOL4006", AgentToolOutputNames.NL2SQL, "sql generated"),
                tool("TOOL4007", AgentToolOutputNames.TABLE_RAG, "schema matched")
        ), List.of());

        Map<String, String> kinds = response.getEvents().stream()
                .filter(event -> "tool_result".equals(event.getEvent()))
                .collect(java.util.stream.Collectors.toMap(
                        event -> String.valueOf(event.getData().get("toolName")),
                        event -> String.valueOf(event.getData().get("resultKind"))));

        assertEquals("code", kinds.get(AgentToolOutputNames.CODE_INTERPRETER));
        assertEquals("data", kinds.get(AgentToolOutputNames.DATA_ANALYSIS));
        assertEquals("image", kinds.get(AgentToolOutputNames.IMAGE_GENERATION));
        assertEquals("web", kinds.get(AgentToolOutputNames.WEB_FETCH));
        assertEquals("multimodal", kinds.get(AgentToolOutputNames.MULTIMODAL_AGENT));
        assertEquals("sql", kinds.get(AgentToolOutputNames.NL2SQL));
        assertEquals("schema", kinds.get(AgentToolOutputNames.TABLE_RAG));
    }

    @Test
    void shouldProjectReplanEventsWhenFailedToolIsRecoveredByLaterTool() {
        AgentRun run = new AgentRun();
        run.setRunId("RUN2001");
        run.setSessionId("S2001");
        run.setRequestId("REQ2001");
        run.setTaskType("deep");
        run.setStatus(AgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(20L);

        AgentToolInvocation failedTool = new AgentToolInvocation();
        failedTool.setInvocationId("TOOL2001");
        failedTool.setToolName(AgentToolOutputNames.CODE_INTERPRETER);
        failedTool.setStatus(AgentRun.STATUS_FAILED);
        failedTool.setErrorMessage("script timeout");

        AgentToolInvocation recoveredTool = new AgentToolInvocation();
        recoveredTool.setInvocationId("TOOL2002");
        recoveredTool.setToolName(AgentToolOutputNames.REPORT_TOOL);
        recoveredTool.setStatus(AgentRun.STATUS_SUCCESS);
        recoveredTool.setResultSummary("report generated");

        AgentReplayResponse response = projector.project(run, List.of(), List.of(failedTool, recoveredTool), List.of());

        long planCount = response.getEvents().stream()
                .filter(event -> "plan_delta".equals(event.getEvent()))
                .count();
        boolean hasReplanFlow = response.getEvents().stream()
                .filter(event -> "flow_delta".equals(event.getEvent()))
                .anyMatch(event -> "REPLANNED".equals(event.getData().get("status")));
        List<String> lifecycle = response.getEvents().stream()
                .filter(event -> "plan_delta".equals(event.getEvent()) || "flow_delta".equals(event.getEvent()))
                .map(event -> event.getEvent() + ":" + event.getData().getOrDefault("status", event.getData().get("changeType")))
                .toList();

        assertEquals(2, planCount);
        assertTrue(hasReplanFlow);
        assertTrue(lifecycle.indexOf("flow_delta:REPLANNED") < lifecycle.indexOf("plan_delta:replan"));
        assertTrue(lifecycle.indexOf("plan_delta:replan") < lifecycle.lastIndexOf("flow_delta:RUNNING"));
    }

    private static AgentToolInvocation tool(String invocationId, String toolName, String summary) {
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setToolName(toolName);
        invocation.setStatus(AgentRun.STATUS_SUCCESS);
        invocation.setResultSummary(summary);
        invocation.setResultJson("""
                {"summary":"ok"}
                """);
        return invocation;
    }
}















