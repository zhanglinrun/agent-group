package com.linrun.domain.agent.ledger.service;

import com.linrun.api.dto.AgentRunDetailResponse;
import com.linrun.api.dto.AgentDiagnosisReportDTO;
import com.linrun.domain.agent.ledger.adapter.AgentExecutionLedgerRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentLlmInvocation;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.diagnosis.AgentDiagnosisService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionLedgerServiceTest {

    @Test
    void shouldExposeRunEvidenceByRunIdForRecoveredToolFailure() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        repository.runs.add(run("RUN1001", "deep", AgentRun.STATUS_SUCCESS));
        repository.llms.add(llm("LLM1001", "RUN1001"));
        repository.tools.add(tool("TOOL1001", "RUN1001", "code_interpreter", AgentRun.STATUS_FAILED,
                "script timeout", ""));
        repository.tools.add(tool("TOOL1002", "RUN1001", "report_tool", AgentRun.STATUS_SUCCESS,
                "", "{\"summary\":\"report generated\",\"metadata\":{\"eventType\":\"replanned\"}}"));
        repository.tools.add(tool("TOOL1003", "RUN1001", AgentToolOutputNames.QUOTA_USAGE, AgentRun.STATUS_SUCCESS,
                "", "{\"metadata\":{\"estimatedConsumedQuota\":12.5}}"));
        repository.tools.add(tool("TOOL1004", "RUN1001", AgentToolOutputNames.QUOTA_USAGE, AgentRun.STATUS_SUCCESS,
                "", "{\"metadata\":{\"estimatedConsumedQuota\":7.5}}"));
        repository.artifacts.add(artifact("ART1001", "RUN1001", "TOOL1002"));

        AgentExecutionLedgerService service = new AgentExecutionLedgerService(repository, new AgentReplayProjector(), AgentObservabilityMetrics.noop(), new AgentDiagnosisService());

        AgentRunDetailResponse detail = service.queryRunDetail("U1", "RUN1001");

        assertEquals("RUN1001", detail.getRun().getRunId());
        assertEquals("Plan-Execute", detail.getEvidence().getMode().getExecutionMode());
        assertEquals("plan-execute", detail.getEvidence().getMode().getModeFamily());
        assertEquals("深度任务", detail.getEvidence().getPlan().getTitle());
        assertEquals(2, detail.getEvidence().getPlan().getRevisionCount());
        assertFalse(detail.getEvidence().getPlan().getSteps().isEmpty());
        assertEquals(4, detail.getEvidence().getToolCallCount());
        assertEquals(1, detail.getEvidence().getFailedToolCount());
        assertEquals(1, detail.getEvidence().getReplanCount());
        assertEquals(1, detail.getEvidence().getLlmCallCount());
        assertEquals(1, detail.getEvidence().getArtifactCount());
        assertEquals(20.0d, detail.getEvidence().getQuotaConsumed());
        assertTrue(detail.getEvidence().getToolSuccessRate() > 0.6d);
        assertEquals(1, detail.getEvidence().getFailedTools().size());
        assertTrue(detail.getEvidence().getFailedTools().getFirst().getRecoveredByLaterTool());
        assertTrue(detail.getEvidence().getReplanReasons().getFirst().contains("script timeout"));
        assertEquals(detail.getDiagnosis().getLevel(), detail.getEvidence().getDiagnosisLevel());
    }

    @Test
    void shouldExposeNormalRunEvidenceWithoutFailureOrReplan() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        repository.runs.add(run("RUN2001", "file", AgentRun.STATUS_SUCCESS));
        repository.tools.add(tool("TOOL2001", "RUN2001", "file_tool", AgentRun.STATUS_SUCCESS,
                "", "{\"summary\":\"file answered\"}"));

        AgentExecutionLedgerService service = new AgentExecutionLedgerService(repository, new AgentReplayProjector(), AgentObservabilityMetrics.noop(), new AgentDiagnosisService());

        AgentRunDetailResponse detail = service.queryRunDetail("U1", "RUN2001");

        assertEquals("ReAct", detail.getEvidence().getMode().getExecutionMode());
        assertEquals("文件问答", detail.getEvidence().getPlan().getTitle());
        assertEquals(1, detail.getEvidence().getPlan().getRevisionCount());
        assertEquals(0, detail.getEvidence().getFailedToolCount());
        assertEquals(0, detail.getEvidence().getReplanCount());
        assertTrue(detail.getEvidence().getFailedTools().isEmpty());
        assertTrue(detail.getEvidence().getReplanReasons().isEmpty());
        assertEquals(1.0d, detail.getEvidence().getToolSuccessRate());
    }

    @Test
    void shouldBuildDiagnosisFromLedgerFacts() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        AgentRun run = run("RUN3001", "deep", AgentRun.STATUS_FAILED);
        run.setErrorMessage("final answer failed");
        repository.runs.add(run);
        repository.tools.add(tool("TOOL3001", "RUN3001", "web_search", AgentRun.STATUS_FAILED,
                "provider unavailable", ""));
        repository.tools.add(tool("TOOL3002", "RUN3001", "report_tool", AgentRun.STATUS_SUCCESS,
                "", "{\"summary\":\"fallback report\"}"));

        AgentExecutionLedgerService service = new AgentExecutionLedgerService(repository, new AgentReplayProjector(), AgentObservabilityMetrics.noop(), new AgentDiagnosisService());

        AgentDiagnosisReportDTO diagnosis = service.queryRunDiagnosis("U1", "RUN3001");

        assertEquals("RUN3001", diagnosis.getRunId());
        assertEquals(2, diagnosis.getToolCallCount());
        assertEquals(1, diagnosis.getFailedToolCount());
        assertEquals(0, diagnosis.getReplanCount());
        assertFalse(diagnosis.getIssues().isEmpty());
    }

    @Test
    void shouldKeepMainFlowWhenLedgerWriteFails() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        repository.failWrites = true;
        AgentExecutionLedgerService service = new AgentExecutionLedgerService(repository, new AgentReplayProjector(), AgentObservabilityMetrics.noop(), new AgentDiagnosisService());

        AgentRun run = assertDoesNotThrow(() -> service.startRun(
                "U1", "S1", "AP1", "REQ1", "chat", "hello", "test-model"));
        assertEquals("AP1", run.getProjectId());
        assertDoesNotThrow(() -> service.finishRun(run, AgentRun.STATUS_SUCCESS,
                "done", "", "", 10L));
        assertDoesNotThrow(() -> service.recordLlmInvocation(
                new AgentLedgerContext.Context(run.getRunId(), "REQ1", "S1", "U1", "chat"),
                "test-model", "prompt", "response", AgentRun.STATUS_SUCCESS,
                false, "", 5L));
        String invocationId = assertDoesNotThrow(() -> service.recordToolStart(
                new AgentLedgerContext.Context(run.getRunId(), "REQ1", "S1", "U1", "chat"),
                "CALL1", "file_tool", "read", "{}"));
        assertEquals("", invocationId);
        assertDoesNotThrow(() -> service.recordToolFinish("TOOL1", AgentRun.STATUS_SUCCESS,
                "ok", "{}", 0, "", 5L));
    }

    private static AgentRun run(String runId, String taskType, String status) {
        AgentRun run = new AgentRun();
        run.setRunId(runId);
        run.setUserId("U1");
        run.setSessionId("S1");
        run.setProjectId("AP1");
        run.setRequestId("REQ-" + runId);
        run.setTaskType(taskType);
        run.setQuestion("分析复杂任务");
        run.setStatus(status);
        run.setModelName("test-model");
        run.setFinalSummary("done");
        run.setStartedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 6, 1, 10, 1));
        run.setDurationMillis(60_000L);
        return run;
    }

    private static AgentLlmInvocation llm(String invocationId, String runId) {
        AgentLlmInvocation invocation = new AgentLlmInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setRunId(runId);
        invocation.setRequestId("REQ-" + runId);
        invocation.setSessionId("S1");
        invocation.setUserId("U1");
        invocation.setModelName("test-model");
        invocation.setPromptSummary("prompt");
        invocation.setResponseText("response");
        invocation.setStatus(AgentRun.STATUS_SUCCESS);
        invocation.setPromptTokens(10L);
        invocation.setCompletionTokens(20L);
        invocation.setTotalTokens(30L);
        return invocation;
    }

    private static AgentToolInvocation tool(String invocationId,
                                               String runId,
                                               String toolName,
                                               String status,
                                               String errorMessage,
                                               String resultJson) {
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setRunId(runId);
        invocation.setRequestId("REQ-" + runId);
        invocation.setSessionId("S1");
        invocation.setUserId("U1");
        invocation.setToolCallId("CALL-" + invocationId);
        invocation.setToolName(toolName);
        invocation.setAction("execute");
        invocation.setArgumentsJson("{}");
        invocation.setResultSummary("summary");
        invocation.setResultJson(resultJson);
        invocation.setStatus(status);
        invocation.setRetryCount(0);
        invocation.setErrorMessage(errorMessage);
        invocation.setStartedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        invocation.setFinishedAt(LocalDateTime.of(2026, 6, 1, 10, 1));
        invocation.setLatencyMillis(1000L);
        return invocation;
    }

    private static AgentArtifact artifact(String artifactId, String runId, String toolInvocationId) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setUserId("U1");
        artifact.setSessionId("S1");
        artifact.setRunId(runId);
        artifact.setToolInvocationId(toolInvocationId);
        artifact.setArtifactType("MD");
        artifact.setTitle("report.md");
        artifact.setContent("report.md");
        artifact.setDownloadUrl("/download/report.md");
        artifact.setSourceType("AGENT");
        artifact.setSourceName("report_tool");
        return artifact;
    }

    private static final class FakeLedgerRepository implements AgentExecutionLedgerRepository {

        private final List<AgentRun> runs = new ArrayList<>();
        private final List<AgentLlmInvocation> llms = new ArrayList<>();
        private final List<AgentToolInvocation> tools = new ArrayList<>();
        private final List<AgentArtifact> artifacts = new ArrayList<>();
        private boolean failWrites;

        @Override
        public void createRun(AgentRun run) {
            failIfNeeded();
            runs.add(run);
        }

        @Override
        public void finishRun(AgentRun run) {
            failIfNeeded();
        }

        @Override
        public void createLlmInvocation(AgentLlmInvocation invocation) {
            failIfNeeded();
            llms.add(invocation);
        }

        @Override
        public void finishLlmInvocation(AgentLlmInvocation invocation) {
            failIfNeeded();
        }

        @Override
        public void createToolInvocation(AgentToolInvocation invocation) {
            failIfNeeded();
            tools.add(invocation);
        }

        @Override
        public void finishToolInvocation(AgentToolInvocation invocation) {
            failIfNeeded();
        }

        @Override
        public void saveArtifact(AgentArtifact artifact) {
            failIfNeeded();
            artifacts.add(artifact);
        }

        @Override
        public Optional<AgentRun> queryRun(String userId, String runId) {
            return runs.stream()
                    .filter(run -> userId.equals(run.getUserId()) && runId.equals(run.getRunId()))
                    .findFirst();
        }

        @Override
        public Optional<AgentRun> queryRunByRequestId(String userId, String requestId) {
            return runs.stream()
                    .filter(run -> userId.equals(run.getUserId()) && requestId.equals(run.getRequestId()))
                    .findFirst();
        }

        @Override
        public Optional<AgentRun> queryLatestRun(String userId, String sessionId) {
            return runs.stream()
                    .filter(run -> userId.equals(run.getUserId()) && sessionId.equals(run.getSessionId()))
                    .findFirst();
        }

        @Override
        public List<AgentRun> queryRuns(String userId, String sessionId, int limit) {
            return runs.stream()
                    .filter(run -> userId.equals(run.getUserId()) && sessionId.equals(run.getSessionId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<AgentLlmInvocation> queryLlmInvocations(String runId) {
            return llms.stream()
                    .filter(invocation -> runId.equals(invocation.getRunId()))
                    .toList();
        }

        @Override
        public List<AgentToolInvocation> queryToolInvocations(String runId) {
            return tools.stream()
                    .filter(invocation -> runId.equals(invocation.getRunId()))
                    .toList();
        }

        @Override
        public List<AgentArtifact> queryArtifactsByRun(String runId) {
            return artifacts.stream()
                    .filter(artifact -> runId.equals(artifact.getRunId()))
                    .toList();
        }

        private void failIfNeeded() {
            if (failWrites) {
                throw new IllegalStateException("ledger unavailable");
            }
        }
    }
}
