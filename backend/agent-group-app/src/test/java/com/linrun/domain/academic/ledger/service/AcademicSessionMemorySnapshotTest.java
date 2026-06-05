package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.ledger.adapter.AcademicExecutionLedgerRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicSessionMemorySnapshotTest {

    @Test
    void shouldBuildSessionMemoryFromLedgerRunsAndReusableArtifacts() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        repository.runs.add(run("RUN3", "REQ3_CURRENT", "S1", "当前请求不应注入", LocalDateTime.of(2026, 6, 1, 10, 20)));
        repository.runs.add(run("RUN2", "REQ2", "S1", "继续分析交易异常", LocalDateTime.of(2026, 6, 1, 10, 10)));
        repository.runs.add(run("RUN1", "REQ1", "S1", "分析拼团转化", LocalDateTime.of(2026, 6, 1, 10, 0)));

        AcademicToolInvocation tool = tool("TOOL1", "RUN1", "data_analysis", "统计漏斗", "已生成分析报告");
        repository.tools.add(tool);
        repository.artifacts.add(artifact("A1", "RUN1", "TOOL1", "REPORT", "conversion.md"));
        repository.artifacts.add(artifact("A2", "RUN1", "TOOL1", "INTERNAL", "debug.json"));

        AcademicExecutionLedgerService service = new AcademicExecutionLedgerService(
                repository, new AcademicReplayProjector());

        AcademicSessionDetailResponse.MemorySnapshot memory =
                service.querySessionMemory("U1", "S1", "REQ3_CURRENT", 8);

        assertEquals("S1", memory.getSessionId());
        assertEquals(2, memory.getRuns().size());
        assertEquals("REQ1", memory.getRuns().get(0).getRequestId());
        assertEquals("REQ2", memory.getRuns().get(1).getRequestId());
        assertEquals(1, memory.getToolObservations().size());
        assertEquals("data_analysis", memory.getToolObservations().get(0).getToolName());
        assertEquals(1, memory.getReusableArtifacts().size());
        assertEquals("conversion.md", memory.getReusableArtifacts().get(0).getFileName());
        assertTrue(memory.getHistoryDialogue().contains("Question: 分析拼团转化"));
        assertTrue(memory.getHistoryDialogue().contains("data_analysis"));
        assertFalse(memory.getHistoryDialogue().contains("REQ3_CURRENT"));
        assertFalse(memory.getHistoryDialogue().contains("debug.json"));
    }

    @Test
    void shouldReturnEmptyMemoryWhenSessionIdIsBlank() {
        AcademicExecutionLedgerService service = new AcademicExecutionLedgerService(
                new FakeLedgerRepository(), new AcademicReplayProjector());

        AcademicSessionDetailResponse.MemorySnapshot memory =
                service.querySessionMemory("U1", "", "", 8);

        assertEquals("", memory.getSessionId());
        assertTrue(memory.getRuns().isEmpty());
        assertTrue(memory.getToolObservations().isEmpty());
    }

    @Test
    void shouldPersistToolFileRefsAsLedgerArtifacts() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        AcademicExecutionLedgerService service = new AcademicExecutionLedgerService(
                repository, new AcademicReplayProjector());

        service.recordToolArtifacts(
                new AcademicLedgerContext.Context("RUN1", "REQ1", "S1", "U1", "data"),
                "TOOL1",
                "report_tool",
                Map.of("fileRefs", List.of(Map.of(
                        "artifactId", "A1001",
                        "fileName", "trade-audit.md",
                        "downloadUrl", "/files/trade-audit.md"))));

        assertEquals(1, repository.artifacts.size());
        AcademicArtifact artifact = repository.artifacts.getFirst();
        assertEquals("A1001", artifact.getArtifactId());
        assertEquals("RUN1", artifact.getRunId());
        assertEquals("TOOL1", artifact.getToolInvocationId());
        assertEquals("TOOL", artifact.getSourceType());
        assertEquals("report_tool", artifact.getSourceName());
        assertEquals("MD", artifact.getArtifactType());
    }

    @Test
    void shouldPersistToolFileInfoAndPrimaryFileFieldsAsLedgerArtifacts() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        AcademicExecutionLedgerService service = new AcademicExecutionLedgerService(
                repository, new AcademicReplayProjector());

        service.recordToolArtifacts(
                new AcademicLedgerContext.Context("RUN1", "REQ1", "S1", "U1", "code"),
                "TOOL1",
                "code_interpreter",
                Map.of(
                        "fileInfo", List.of(Map.of(
                                "displayName", "code-output.md",
                                "domainUrl", "/tool/files/code-output.md",
                                "mimeType", "text/markdown",
                                "resourceKey", "code-output-resource",
                                "size", 512)),
                        "result", Map.of(
                                "primaryFileName", "summary.csv",
                                "ossUrl", "/files/summary.csv",
                                "mimeType", "text/csv")));

        assertEquals(2, repository.artifacts.size());
        AcademicArtifact codeOutput = repository.artifacts.get(0);
        AcademicArtifact summary = repository.artifacts.get(1);
        assertEquals("code-output-resource", codeOutput.getArtifactId());
        assertEquals("code-output.md", codeOutput.getTitle());
        assertEquals("/tool/files/code-output.md", codeOutput.getDownloadUrl());
        assertEquals("MD", codeOutput.getArtifactType());
        assertEquals("summary.csv", summary.getTitle());
        assertEquals("/files/summary.csv", summary.getDownloadUrl());
        assertEquals("CSV", summary.getArtifactType());
    }

    private static AcademicAgentRun run(String runId,
                                        String requestId,
                                        String sessionId,
                                        String question,
                                        LocalDateTime startedAt) {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId(runId);
        run.setRequestId(requestId);
        run.setSessionId(sessionId);
        run.setUserId("U1");
        run.setTaskType("data");
        run.setQuestion(question);
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setFinalSummary("完成 " + question);
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusSeconds(5));
        return run;
    }

    private static AcademicToolInvocation tool(String invocationId,
                                               String runId,
                                               String toolName,
                                               String action,
                                               String summary) {
        AcademicToolInvocation invocation = new AcademicToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setRunId(runId);
        invocation.setRequestId("REQ1");
        invocation.setSessionId("S1");
        invocation.setUserId("U1");
        invocation.setToolCallId("CALL1");
        invocation.setToolName(toolName);
        invocation.setAction(action);
        invocation.setArgumentsJson("{\"metric\":\"conversion\"}");
        invocation.setResultSummary(summary);
        invocation.setResultJson("""
                {"summary":"已生成分析报告","fileRefs":[{"artifactId":"A1","fileName":"conversion.md"}]}
                """);
        invocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        invocation.setStartedAt(LocalDateTime.of(2026, 6, 1, 10, 1));
        invocation.setFinishedAt(LocalDateTime.of(2026, 6, 1, 10, 2));
        return invocation;
    }

    private static AcademicArtifact artifact(String artifactId,
                                             String runId,
                                             String toolInvocationId,
                                             String artifactType,
                                             String content) {
        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setSessionId("S1");
        artifact.setUserId("U1");
        artifact.setRunId(runId);
        artifact.setToolInvocationId(toolInvocationId);
        artifact.setArtifactType(artifactType);
        artifact.setTitle(content);
        artifact.setContent(content);
        artifact.setDownloadUrl("/download/" + content);
        artifact.setSourceType("AGENT");
        artifact.setSourceName("tool");
        artifact.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 3));
        return artifact;
    }

    private static final class FakeLedgerRepository implements AcademicExecutionLedgerRepository {

        private final List<AcademicAgentRun> runs = new ArrayList<>();
        private final List<AcademicToolInvocation> tools = new ArrayList<>();
        private final List<AcademicArtifact> artifacts = new ArrayList<>();

        @Override
        public void createRun(AcademicAgentRun run) {
        }

        @Override
        public void finishRun(AcademicAgentRun run) {
        }

        @Override
        public void createLlmInvocation(AcademicLlmInvocation invocation) {
        }

        @Override
        public void finishLlmInvocation(AcademicLlmInvocation invocation) {
        }

        @Override
        public void createToolInvocation(AcademicToolInvocation invocation) {
        }

        @Override
        public void finishToolInvocation(AcademicToolInvocation invocation) {
        }

        @Override
        public void saveArtifact(AcademicArtifact artifact) {
            artifacts.add(artifact);
        }

        @Override
        public Optional<AcademicAgentRun> queryRun(String userId, String runId) {
            return runs.stream().filter(run -> runId.equals(run.getRunId())).findFirst();
        }

        @Override
        public Optional<AcademicAgentRun> queryRunByRequestId(String userId, String requestId) {
            return runs.stream().filter(run -> requestId.equals(run.getRequestId())).findFirst();
        }

        @Override
        public Optional<AcademicAgentRun> queryLatestRun(String userId, String sessionId) {
            return runs.stream().findFirst();
        }

        @Override
        public List<AcademicAgentRun> queryRuns(String userId, String sessionId, int limit) {
            return runs.stream()
                    .filter(run -> userId.equals(run.getUserId()) && sessionId.equals(run.getSessionId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<AcademicLlmInvocation> queryLlmInvocations(String runId) {
            return List.of();
        }

        @Override
        public List<AcademicToolInvocation> queryToolInvocations(String runId) {
            return tools.stream()
                    .filter(tool -> runId.equals(tool.getRunId()))
                    .toList();
        }

        @Override
        public List<AcademicArtifact> queryArtifactsByRun(String runId) {
            return artifacts.stream()
                    .filter(artifact -> runId.equals(artifact.getRunId()))
                    .toList();
        }
    }
}
