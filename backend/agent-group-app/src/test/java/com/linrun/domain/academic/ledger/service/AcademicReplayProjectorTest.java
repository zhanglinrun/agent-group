package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.QuotaStreamEvent;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicReplayProjectorTest {

    private final AcademicReplayProjector projector = new AcademicReplayProjector();

    @Test
    void shouldProjectStructuredToolResultIntoReplayEvents() {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN1001");
        run.setSessionId("S1001");
        run.setRequestId("REQ1001");
        run.setTaskType("deep");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(10L);

        AcademicToolInvocation toolInvocation = new AcademicToolInvocation();
        toolInvocation.setInvocationId("TOOL1001");
        toolInvocation.setToolCallId("CALL1001");
        toolInvocation.setToolName(AcademicToolOutputNames.REPORT_TOOL);
        toolInvocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        toolInvocation.setResultSummary("报告已生成);
        toolInvocation.setResultJson("""
                {
                  "toolName": "report_tool",
                  "summary": "报告已生成,
                  "fileRefs": [{"artifactId": "A2001", "fileName": "report.md"}]
                }
                """);

        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId("A2001");
        artifact.setToolInvocationId("TOOL1001");
        artifact.setTitle("报告");
        artifact.setContent("report.md");

        AcademicReplayResponse response = projector.project(run, List.of(), List.of(toolInvocation), List.of(artifact));

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
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN4001");
        run.setSessionId("S4001");
        run.setRequestId("REQ4001");
        run.setTaskType("deep");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());

        AcademicReplayResponse response = projector.project(run, List.of(), List.of(
                tool("TOOL4001", AcademicToolOutputNames.CODE_INTERPRETER, "generated code"),
                tool("TOOL4002", AcademicToolOutputNames.DATA_ANALYSIS, "rows analyzed"),
                tool("TOOL4003", AcademicToolOutputNames.IMAGE_GENERATION, "image generated"),
                tool("TOOL4004", AcademicToolOutputNames.WEB_FETCH, "page fetched"),
                tool("TOOL4005", AcademicToolOutputNames.MULTIMODAL_AGENT, "image understood"),
                tool("TOOL4006", AcademicToolOutputNames.NL2SQL, "sql generated"),
                tool("TOOL4007", AcademicToolOutputNames.TABLE_RAG, "schema matched")
        ), List.of());

        Map<String, String> kinds = response.getEvents().stream()
                .filter(event -> "tool_result".equals(event.getEvent()))
                .collect(java.util.stream.Collectors.toMap(
                        event -> String.valueOf(event.getData().get("toolName")),
                        event -> String.valueOf(event.getData().get("resultKind"))));

        assertEquals("code", kinds.get(AcademicToolOutputNames.CODE_INTERPRETER));
        assertEquals("data", kinds.get(AcademicToolOutputNames.DATA_ANALYSIS));
        assertEquals("image", kinds.get(AcademicToolOutputNames.IMAGE_GENERATION));
        assertEquals("web", kinds.get(AcademicToolOutputNames.WEB_FETCH));
        assertEquals("multimodal", kinds.get(AcademicToolOutputNames.MULTIMODAL_AGENT));
        assertEquals("sql", kinds.get(AcademicToolOutputNames.NL2SQL));
        assertEquals("schema", kinds.get(AcademicToolOutputNames.TABLE_RAG));
    }

    @Test
    void shouldProjectReplanEventsWhenFailedToolIsRecoveredByLaterTool() {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN2001");
        run.setSessionId("S2001");
        run.setRequestId("REQ2001");
        run.setTaskType("deep");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(20L);

        AcademicToolInvocation failedTool = new AcademicToolInvocation();
        failedTool.setInvocationId("TOOL2001");
        failedTool.setToolName(AcademicToolOutputNames.CODE_INTERPRETER);
        failedTool.setStatus(AcademicAgentRun.STATUS_FAILED);
        failedTool.setErrorMessage("script timeout");

        AcademicToolInvocation recoveredTool = new AcademicToolInvocation();
        recoveredTool.setInvocationId("TOOL2002");
        recoveredTool.setToolName(AcademicToolOutputNames.REPORT_TOOL);
        recoveredTool.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        recoveredTool.setResultSummary("report generated");

        AcademicReplayResponse response = projector.project(run, List.of(), List.of(failedTool, recoveredTool), List.of());

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

    private static AcademicToolInvocation tool(String invocationId, String toolName, String summary) {
        AcademicToolInvocation invocation = new AcademicToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setToolName(toolName);
        invocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        invocation.setResultSummary(summary);
        invocation.setResultJson("""
                {"summary":"ok"}
                """);
        return invocation;
    }
}















