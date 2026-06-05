package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.GuideStreamEvent;
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
        toolInvocation.setToolName(AcademicToolOutputNames.REPORT_TOOL);
        toolInvocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        toolInvocation.setResultSummary("报告已生成");
        toolInvocation.setResultJson("""
                {
                  "toolName": "report_tool",
                  "summary": "报告已生成",
                  "fileRefs": [{"artifactId": "A2001", "fileName": "report.md"}]
                }
                """);

        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId("A2001");
        artifact.setToolInvocationId("TOOL1001");
        artifact.setTitle("报告");
        artifact.setContent("report.md");

        AcademicReplayResponse response = projector.project(run, List.of(), List.of(toolInvocation), List.of(artifact));

        GuideStreamEvent<Map<String, Object>> toolResult = response.getEvents().stream()
                .filter(event -> "tool_result".equals(event.getEvent()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, toolResult.getData().get("artifactCount"));
        assertFalse(((Map<?, ?>) toolResult.getData().get("structuredOutput")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReplayTradeAuditOutputWithArtifactRefs() {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN3001");
        run.setSessionId("S3001");
        run.setRequestId("REQ3001");
        run.setTaskType("trade-audit");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(30L);

        AcademicToolInvocation toolInvocation = new AcademicToolInvocation();
        toolInvocation.setInvocationId("TOOL3001");
        toolInvocation.setToolName(AcademicToolOutputNames.TRADE_AUDIT);
        toolInvocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        toolInvocation.setResultSummary("trade facts checked");
        toolInvocation.setResultJson("""
                {
                  "toolName": "trade_audit",
                  "title": "O3001",
                  "summary": "trade facts checked",
                  "metadata": {
                    "snapshot": {"orderId": "O3001"},
                    "findings": [
                      {"severity": "INFO", "code": "NO_BLOCKING_RISK"}
                    ],
                    "highestSeverity": "INFO",
                    "reportMaterialized": true
                  },
                  "fileRefs": [{"artifactId": "A3001", "fileName": "trade-audit-O3001.md"}]
                }
                """);

        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId("A3001");
        artifact.setToolInvocationId("TOOL3001");
        artifact.setTitle("Trade Audit");
        artifact.setContent("trade-audit-O3001.md");
        artifact.setArtifactType("MD");
        artifact.setDownloadUrl("/artifacts/A3001");

        AcademicReplayResponse response = projector.project(run, List.of(), List.of(toolInvocation), List.of(artifact));

        GuideStreamEvent<Map<String, Object>> toolResult = response.getEvents().stream()
                .filter(event -> "tool_result".equals(event.getEvent()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> structuredOutput = (Map<String, Object>) toolResult.getData().get("structuredOutput");
        List<Map<String, Object>> fileRefs = (List<Map<String, Object>>) toolResult.getData().get("fileRefs");
        List<Map<String, Object>> artifactRefs = (List<Map<String, Object>>) toolResult.getData().get("artifactRefs");

        assertEquals("trade", structuredOutput.get("auditKind"));
        assertEquals(1, structuredOutput.get("findingCount"));
        assertEquals("/artifacts/A3001", fileRefs.getFirst().get("downloadUrl"));
        assertEquals("A3001", artifactRefs.getFirst().get("artifactId"));
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

        assertEquals(2, planCount);
        assertTrue(hasReplanFlow);
    }
}
