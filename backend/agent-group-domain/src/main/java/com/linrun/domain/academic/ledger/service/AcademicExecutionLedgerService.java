package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.ledger.adapter.AcademicExecutionLedgerRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AcademicExecutionLedgerService {

    private static final int DEFAULT_RUN_LIMIT = 5;

    private final AcademicExecutionLedgerRepository ledgerRepository;
    private final AcademicReplayProjector replayProjector;

    public AcademicExecutionLedgerService(AcademicExecutionLedgerRepository ledgerRepository,
                                          AcademicReplayProjector replayProjector) {
        this.ledgerRepository = ledgerRepository;
        this.replayProjector = replayProjector;
    }

    public AcademicAgentRun startRun(String userId,
                                     String sessionId,
                                     String requestId,
                                     String taskType,
                                     String question,
                                     String modelName) {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId(nextId("RUN"));
        run.setUserId(safe(userId));
        run.setSessionId(safe(sessionId));
        run.setRequestId(safe(requestId));
        run.setTaskType(safe(taskType));
        run.setQuestion(limit(question, 2048));
        run.setStatus(AcademicAgentRun.STATUS_RUNNING);
        run.setModelName(safe(modelName));
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(0L);
        try {
            ledgerRepository.createRun(run);
        } catch (Exception ignored) {
        }
        return run;
    }

    public void finishRun(AcademicAgentRun run,
                          String status,
                          String summary,
                          String errorCode,
                          String errorMessage,
                          long durationMillis) {
        if (run == null || !StringUtils.hasText(run.getRunId())) {
            return;
        }
        run.setStatus(StringUtils.hasText(status) ? status : AcademicAgentRun.STATUS_SUCCESS);
        run.setFinalSummary(limit(summary, 4000));
        run.setErrorCode(safe(errorCode));
        run.setErrorMessage(limit(errorMessage, 1024));
        run.setFinishedAt(LocalDateTime.now());
        run.setDurationMillis(Math.max(0L, durationMillis));
        try {
            ledgerRepository.finishRun(run);
        } catch (Exception ignored) {
        }
    }

    public void recordLlmInvocation(AcademicLedgerContext.Context context,
                                    String modelName,
                                    String promptSummary,
                                    String responseText,
                                    String status,
                                    boolean fallbackUsed,
                                    String errorMessage,
                                    long latencyMillis) {
        if (context == null || !StringUtils.hasText(context.runId())) {
            return;
        }
        AcademicLlmInvocation invocation = new AcademicLlmInvocation();
        invocation.setInvocationId(nextId("LLM"));
        invocation.setRunId(context.runId());
        invocation.setRequestId(context.requestId());
        invocation.setSessionId(context.sessionId());
        invocation.setUserId(context.userId());
        invocation.setModelName(safe(modelName));
        invocation.setPromptSummary(limit(promptSummary, 2048));
        invocation.setResponseText(limit(responseText, 12000));
        invocation.setStatus(StringUtils.hasText(status) ? status : AcademicAgentRun.STATUS_SUCCESS);
        long promptTokens = estimateTokens(promptSummary);
        long completionTokens = estimateTokens(responseText);
        invocation.setPromptTokens(promptTokens);
        invocation.setCompletionTokens(completionTokens);
        invocation.setTotalTokens(promptTokens + completionTokens);
        invocation.setFallbackUsed(fallbackUsed);
        invocation.setErrorMessage(limit(errorMessage, 1024));
        invocation.setStartedAt(LocalDateTime.now().minusNanos(Math.max(0L, latencyMillis) * 1_000_000L));
        invocation.setFinishedAt(LocalDateTime.now());
        invocation.setLatencyMillis(Math.max(0L, latencyMillis));
        try {
            ledgerRepository.createLlmInvocation(invocation);
        } catch (Exception ignored) {
        }
    }

    public String recordToolStart(AcademicLedgerContext.Context context,
                                  String toolCallId,
                                  String toolName,
                                  String action,
                                  String argumentsJson) {
        if (context == null || !StringUtils.hasText(context.runId())) {
            return "";
        }
        AcademicToolInvocation invocation = new AcademicToolInvocation();
        invocation.setInvocationId(nextId("TOOL"));
        invocation.setRunId(context.runId());
        invocation.setRequestId(context.requestId());
        invocation.setSessionId(context.sessionId());
        invocation.setUserId(context.userId());
        invocation.setToolCallId(safe(toolCallId));
        invocation.setToolName(safe(toolName));
        invocation.setAction(safe(action));
        invocation.setArgumentsJson(limit(argumentsJson, 6000));
        invocation.setStatus(AcademicAgentRun.STATUS_RUNNING);
        invocation.setRetryCount(0);
        invocation.setStartedAt(LocalDateTime.now());
        invocation.setLatencyMillis(0L);
        try {
            ledgerRepository.createToolInvocation(invocation);
            return invocation.getInvocationId();
        } catch (Exception ignored) {
            return "";
        }
    }

    public void recordToolFinish(String invocationId,
                                 String status,
                                 String resultSummary,
                                 String resultJson,
                                 int retryCount,
                                 String errorMessage,
                                 long latencyMillis) {
        if (!StringUtils.hasText(invocationId)) {
            return;
        }
        AcademicToolInvocation invocation = new AcademicToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setStatus(StringUtils.hasText(status) ? status : AcademicAgentRun.STATUS_SUCCESS);
        invocation.setResultSummary(limit(resultSummary, 1024));
        invocation.setResultJson(limit(resultJson, 12000));
        invocation.setRetryCount(Math.max(0, retryCount));
        invocation.setErrorMessage(limit(errorMessage, 1024));
        invocation.setFinishedAt(LocalDateTime.now());
        invocation.setLatencyMillis(Math.max(0L, latencyMillis));
        try {
            ledgerRepository.finishToolInvocation(invocation);
        } catch (Exception ignored) {
        }
    }

    public List<AcademicAgentRun> queryRuns(String userId, String sessionId, int limit) {
        return ledgerRepository.queryRuns(userId, sessionId, Math.max(1, Math.min(limit, 50)));
    }

    public AcademicRunDetailResponse queryRunDetail(String userId, String runId) {
        AcademicAgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        return detail(run);
    }

    public List<AcademicReplayResponse> querySessionReplays(String userId, String sessionId) {
        return queryRuns(userId, sessionId, DEFAULT_RUN_LIMIT).stream()
                .map(this::replay)
                .toList();
    }

    public AcademicReplayResponse queryRunReplay(String userId, String runId) {
        AcademicAgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        return replay(run);
    }

    private AcademicRunDetailResponse detail(AcademicAgentRun run) {
        AcademicRunDetailResponse response = new AcademicRunDetailResponse();
        response.setRun(run(run));
        response.setLlmInvocations(ledgerRepository.queryLlmInvocations(run.getRunId()).stream()
                .map(this::llm)
                .toList());
        response.setToolInvocations(ledgerRepository.queryToolInvocations(run.getRunId()).stream()
                .map(this::tool)
                .toList());
        response.setArtifacts(ledgerRepository.queryArtifactsByRun(run.getRunId()).stream()
                .map(this::artifact)
                .toList());
        return response;
    }

    private AcademicReplayResponse replay(AcademicAgentRun run) {
        return replayProjector.project(run,
                ledgerRepository.queryLlmInvocations(run.getRunId()),
                ledgerRepository.queryToolInvocations(run.getRunId()),
                ledgerRepository.queryArtifactsByRun(run.getRunId()));
    }

    private AcademicRunDetailResponse.Run run(AcademicAgentRun run) {
        AcademicRunDetailResponse.Run dto = new AcademicRunDetailResponse.Run();
        dto.setRunId(run.getRunId());
        dto.setSessionId(run.getSessionId());
        dto.setRequestId(run.getRequestId());
        dto.setTaskType(run.getTaskType());
        dto.setQuestion(run.getQuestion());
        dto.setStatus(run.getStatus());
        dto.setModelName(run.getModelName());
        dto.setFinalSummary(run.getFinalSummary());
        dto.setErrorCode(run.getErrorCode());
        dto.setErrorMessage(run.getErrorMessage());
        dto.setStartedAt(run.getStartedAt());
        dto.setFinishedAt(run.getFinishedAt());
        dto.setDurationMillis(run.getDurationMillis());
        return dto;
    }

    private AcademicRunDetailResponse.LlmInvocation llm(AcademicLlmInvocation invocation) {
        AcademicRunDetailResponse.LlmInvocation dto = new AcademicRunDetailResponse.LlmInvocation();
        dto.setInvocationId(invocation.getInvocationId());
        dto.setModelName(invocation.getModelName());
        dto.setPromptSummary(invocation.getPromptSummary());
        dto.setResponseText(invocation.getResponseText());
        dto.setStatus(invocation.getStatus());
        dto.setPromptTokens(invocation.getPromptTokens());
        dto.setCompletionTokens(invocation.getCompletionTokens());
        dto.setTotalTokens(invocation.getTotalTokens());
        dto.setFallbackUsed(invocation.getFallbackUsed());
        dto.setErrorMessage(invocation.getErrorMessage());
        dto.setStartedAt(invocation.getStartedAt());
        dto.setFinishedAt(invocation.getFinishedAt());
        dto.setLatencyMillis(invocation.getLatencyMillis());
        return dto;
    }

    private AcademicRunDetailResponse.ToolInvocation tool(AcademicToolInvocation invocation) {
        AcademicRunDetailResponse.ToolInvocation dto = new AcademicRunDetailResponse.ToolInvocation();
        dto.setInvocationId(invocation.getInvocationId());
        dto.setToolCallId(invocation.getToolCallId());
        dto.setToolName(invocation.getToolName());
        dto.setAction(invocation.getAction());
        dto.setArgumentsJson(invocation.getArgumentsJson());
        dto.setResultSummary(invocation.getResultSummary());
        dto.setResultJson(invocation.getResultJson());
        dto.setStatus(invocation.getStatus());
        dto.setRetryCount(invocation.getRetryCount());
        dto.setErrorMessage(invocation.getErrorMessage());
        dto.setStartedAt(invocation.getStartedAt());
        dto.setFinishedAt(invocation.getFinishedAt());
        dto.setLatencyMillis(invocation.getLatencyMillis());
        return dto;
    }

    private AcademicSessionDetailResponse.Artifact artifact(AcademicArtifact artifact) {
        AcademicSessionDetailResponse.Artifact dto = new AcademicSessionDetailResponse.Artifact();
        dto.setArtifactId(artifact.getArtifactId());
        dto.setArtifactType(artifact.getArtifactType());
        dto.setTitle(artifact.getTitle());
        dto.setFileName(fileName(artifact));
        dto.setDownloadUrl(artifact.getDownloadUrl());
        dto.setRunId(artifact.getRunId());
        dto.setToolInvocationId(artifact.getToolInvocationId());
        dto.setSourceType(artifact.getSourceType());
        dto.setSourceName(artifact.getSourceName());
        return dto;
    }

    private String fileName(AcademicArtifact artifact) {
        String content = safe(artifact.getContent());
        int slash = Math.max(content.lastIndexOf('/'), content.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < content.length()) {
            return content.substring(slash + 1);
        }
        return StringUtils.hasText(content) ? content : safe(artifact.getTitle());
    }

    private long estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        long cjkTokens = 0L;
        long otherChars = 0L;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                cjkTokens++;
            } else {
                otherChars++;
            }
        }
        return cjkTokens + (long) Math.ceil(otherChars / 4.0d);
    }

    private String nextId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String limit(String value, int maxLength) {
        String text = safe(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
