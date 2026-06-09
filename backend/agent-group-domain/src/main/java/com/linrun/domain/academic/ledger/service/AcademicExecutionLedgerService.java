package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.ledger.adapter.AcademicExecutionLedgerRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputReader;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputView;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AcademicExecutionLedgerService {

    private static final int DEFAULT_RUN_LIMIT = 5;

    private final AcademicExecutionLedgerRepository ledgerRepository;
    private final AcademicReplayProjector replayProjector;
    private final AcademicToolOutputReader toolOutputReader = new AcademicToolOutputReader();

    public AcademicExecutionLedgerService(AcademicExecutionLedgerRepository ledgerRepository,
                                          AcademicReplayProjector replayProjector) {
        this.ledgerRepository = ledgerRepository;
        this.replayProjector = replayProjector;
    }

    public AcademicAgentRun startRun(String userId,
                                     String sessionId,
                                     String projectId,
                                     String requestId,
                                     String taskType,
                                     String question,
                                     String modelName) {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId(nextId("RUN"));
        run.setUserId(safe(userId));
        run.setSessionId(safe(sessionId));
        run.setProjectId(safe(projectId));
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

    public void recordToolArtifacts(AcademicLedgerContext.Context context,
                                    String toolInvocationId,
                                    String toolName,
                                    Map<String, Object> result) {
        if (context == null
                || !StringUtils.hasText(context.runId())
                || !StringUtils.hasText(context.userId())
                || !StringUtils.hasText(context.sessionId())) {
            return;
        }
        List<AcademicToolFileRef> fileRefs = fileRefs(result);
        if (fileRefs.isEmpty()) {
            return;
        }
        for (AcademicToolFileRef fileRef : fileRefs) {
            AcademicArtifact artifact = artifactFromFileRef(context, toolInvocationId, toolName, fileRef);
            if (artifact == null) {
                continue;
            }
            try {
                ledgerRepository.saveArtifact(artifact);
            } catch (Exception ignored) {
            }
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

    public AcademicSessionDetailResponse.MemorySnapshot querySessionMemory(String userId,
                                                                           String sessionId,
                                                                           String currentRequestId,
                                                                           int limit) {
        AcademicSessionDetailResponse.MemorySnapshot memory = new AcademicSessionDetailResponse.MemorySnapshot();
        memory.setSessionId(safe(sessionId));
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return memory;
        }
        List<AcademicAgentRun> latestRuns = ledgerRepository.queryRuns(
                userId, sessionId, Math.max(1, Math.min(limit, 20)));
        List<AcademicAgentRun> runs = new ArrayList<>();
        for (int index = latestRuns.size() - 1; index >= 0; index--) {
            AcademicAgentRun run = latestRuns.get(index);
            if (same(run.getRequestId(), currentRequestId)) {
                continue;
            }
            runs.add(run);
        }

        List<AcademicSessionDetailResponse.ToolObservation> observations = new ArrayList<>();
        Map<String, AcademicSessionDetailResponse.Artifact> reusableArtifacts = new LinkedHashMap<>();
        StringBuilder dialogue = new StringBuilder("## Session Memory\n");
        for (AcademicAgentRun run : runs) {
            List<AcademicToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
            List<AcademicArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
            memory.getRuns().add(runMemory(run));
            appendRunDialogue(dialogue, run, toolInvocations, artifacts);
            for (AcademicArtifact artifact : artifacts) {
                if (isReusableArtifact(artifact)) {
                    reusableArtifacts.put(artifact.getArtifactId(), artifact(artifact));
                }
            }
            for (AcademicToolInvocation invocation : toolInvocations) {
                AcademicSessionDetailResponse.ToolObservation observation = toolObservation(invocation, artifacts);
                observations.add(observation);
                for (AcademicSessionDetailResponse.Artifact artifact : observation.getArtifactRefs()) {
                    reusableArtifacts.put(artifact.getArtifactId(), artifact);
                }
            }
        }
        memory.setToolObservations(observations);
        memory.setReusableArtifacts(new ArrayList<>(reusableArtifacts.values()));
        memory.setHistoryDialogue(dialogue.toString().trim());
        memory.setSummary(memorySummary(memory));
        return memory;
    }

    public AcademicReplayResponse queryRunReplay(String userId, String runId) {
        AcademicAgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        return replay(run);
    }

    private AcademicRunDetailResponse detail(AcademicAgentRun run) {
        List<AcademicLlmInvocation> llmInvocations = ledgerRepository.queryLlmInvocations(run.getRunId());
        List<AcademicToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
        List<AcademicArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
        AcademicRunDetailResponse response = new AcademicRunDetailResponse();
        response.setRun(run(run));
        response.setLlmInvocations(llmInvocations.stream()
                .map(this::llm)
                .toList());
        response.setToolInvocations(toolInvocations.stream()
                .map(invocation -> tool(invocation, artifacts))
                .toList());
        response.setArtifacts(artifacts.stream()
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
        dto.setProjectId(run.getProjectId());
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

    private AcademicSessionDetailResponse.RunMemory runMemory(AcademicAgentRun run) {
        AcademicSessionDetailResponse.RunMemory dto = new AcademicSessionDetailResponse.RunMemory();
        dto.setRunId(run.getRunId());
        dto.setRequestId(run.getRequestId());
        dto.setTaskType(run.getTaskType());
        dto.setQuestion(run.getQuestion());
        dto.setStatus(run.getStatus());
        dto.setFinalSummary(run.getFinalSummary());
        dto.setStartedAt(run.getStartedAt());
        dto.setFinishedAt(run.getFinishedAt());
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

    private AcademicRunDetailResponse.ToolInvocation tool(AcademicToolInvocation invocation,
                                                          List<AcademicArtifact> artifacts) {
        AcademicToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
        AcademicRunDetailResponse.ToolInvocation dto = new AcademicRunDetailResponse.ToolInvocation();
        dto.setInvocationId(invocation.getInvocationId());
        dto.setToolCallId(invocation.getToolCallId());
        dto.setToolName(invocation.getToolName());
        dto.setAction(invocation.getAction());
        dto.setArgumentsJson(invocation.getArgumentsJson());
        dto.setResultSummary(invocation.getResultSummary());
        dto.setResultJson(invocation.getResultJson());
        dto.setStructuredOutput(outputView.getStructuredOutput());
        dto.setArtifactRefs(outputView.getArtifactRefs().stream()
                .map(this::artifact)
                .toList());
        dto.setArtifactCount(outputView.getArtifactCount());
        dto.setStatus(invocation.getStatus());
        dto.setRetryCount(invocation.getRetryCount());
        dto.setErrorMessage(invocation.getErrorMessage());
        dto.setStartedAt(invocation.getStartedAt());
        dto.setFinishedAt(invocation.getFinishedAt());
        dto.setLatencyMillis(invocation.getLatencyMillis());
        return dto;
    }

    private AcademicSessionDetailResponse.ToolObservation toolObservation(AcademicToolInvocation invocation,
                                                                          List<AcademicArtifact> artifacts) {
        AcademicToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
        AcademicSessionDetailResponse.ToolObservation dto = new AcademicSessionDetailResponse.ToolObservation();
        dto.setRunId(invocation.getRunId());
        dto.setInvocationId(invocation.getInvocationId());
        dto.setToolCallId(invocation.getToolCallId());
        dto.setToolName(invocation.getToolName());
        dto.setAction(invocation.getAction());
        dto.setArgumentsJson(invocation.getArgumentsJson());
        dto.setResultSummary(firstText(invocation.getResultSummary(), text(outputView.getStructuredOutput().get("summary"))));
        dto.setStatus(invocation.getStatus());
        dto.setErrorMessage(invocation.getErrorMessage());
        dto.setCreatedAt(outputView.getCreatedAt());
        dto.setArtifactRefs(outputView.getArtifactRefs().stream()
                .filter(this::isReusableArtifact)
                .map(this::artifact)
                .toList());
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

    private void appendRunDialogue(StringBuilder dialogue,
                                   AcademicAgentRun run,
                                   List<AcademicToolInvocation> toolInvocations,
                                   List<AcademicArtifact> artifacts) {
        dialogue.append("\n### Run ").append(firstText(run.getRequestId(), run.getRunId())).append('\n');
        appendLine(dialogue, "Task", run.getTaskType());
        appendLine(dialogue, "Question", run.getQuestion());
        appendLine(dialogue, "Status", run.getStatus());
        appendLine(dialogue, "Summary", run.getFinalSummary());
        if (toolInvocations == null || toolInvocations.isEmpty()) {
            dialogue.append("Tool Observations: none\n");
        } else {
            dialogue.append("Tool Observations:\n");
            for (AcademicToolInvocation invocation : toolInvocations) {
                AcademicToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
                dialogue.append("- ").append(firstText(invocation.getToolName(), "tool"))
                        .append(" [").append(firstText(invocation.getStatus(), "UNKNOWN")).append("]");
                String summary = firstText(invocation.getResultSummary(), text(outputView.getStructuredOutput().get("summary")));
                if (StringUtils.hasText(summary)) {
                    dialogue.append(": ").append(limit(summary, 300));
                }
                if (!outputView.getArtifactRefs().isEmpty()) {
                    dialogue.append(" files=");
                    dialogue.append(outputView.getArtifactRefs().stream()
                            .filter(this::isReusableArtifact)
                            .map(this::fileName)
                            .filter(StringUtils::hasText)
                            .toList());
                }
                dialogue.append('\n');
            }
        }
    }

    private void appendLine(StringBuilder dialogue, String label, String value) {
        if (StringUtils.hasText(value)) {
            dialogue.append(label).append(": ").append(limit(value, 500)).append('\n');
        }
    }

    private String memorySummary(AcademicSessionDetailResponse.MemorySnapshot memory) {
        String latestQuestion = memory.getRuns().isEmpty()
                ? ""
                : safe(memory.getRuns().get(memory.getRuns().size() - 1).getQuestion());
        List<String> parts = new ArrayList<>();
        parts.add("runs=" + memory.getRuns().size());
        parts.add("toolObservations=" + memory.getToolObservations().size());
        parts.add("reusableArtifacts=" + memory.getReusableArtifacts().size());
        if (StringUtils.hasText(latestQuestion)) {
            parts.add("latestQuestion=" + limit(latestQuestion, 120));
        }
        return String.join(", ", parts);
    }

    private boolean isReusableArtifact(AcademicArtifact artifact) {
        if (artifact == null) {
            return false;
        }
        return !"INTERNAL".equalsIgnoreCase(artifact.getArtifactType())
                && !"INTERNAL".equalsIgnoreCase(artifact.getSourceType());
    }

    private String fileName(AcademicArtifact artifact) {
        String content = safe(artifact.getContent());
        int slash = Math.max(content.lastIndexOf('/'), content.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < content.length()) {
            return content.substring(slash + 1);
        }
        return StringUtils.hasText(content) ? content : safe(artifact.getTitle());
    }

    @SuppressWarnings("unchecked")
    private List<AcademicToolFileRef> fileRefs(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return List.of();
        }
        List<AcademicToolFileRef> refs = new ArrayList<>();
        collectFileRefs(result.get("fileRefs"), refs);
        collectFileRefs(result.get("artifactRefs"), refs);
        collectFileRefs(result.get("fileInfo"), refs);
        collectFileRefs(result.get("fileList"), refs);
        collectPrimaryFileRef(result, refs);
        collectNestedFileRefs(result.get("result"), refs);
        collectNestedFileRefs(result.get("resultMap"), refs);
        collectNestedFileRefs(result.get("structuredOutput"), refs);
        Map<String, AcademicToolFileRef> deduped = new LinkedHashMap<>();
        for (AcademicToolFileRef ref : refs) {
            String key = firstText(ref.getArtifactId(), ref.getDownloadUrl(), ref.getPreviewUrl(), ref.getFileName());
            if (StringUtils.hasText(key)) {
                deduped.putIfAbsent(key, ref);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    @SuppressWarnings("unchecked")
    private void collectFileRefs(Object value, List<AcademicToolFileRef> refs) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                refs.add(AcademicToolFileRef.fromMap((Map<String, Object>) map));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectNestedFileRefs(Object value, List<AcademicToolFileRef> refs) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = (Map<String, Object>) map;
            collectFileRefs(nested.get("fileRefs"), refs);
            collectFileRefs(nested.get("artifactRefs"), refs);
            collectFileRefs(nested.get("fileInfo"), refs);
            collectFileRefs(nested.get("fileList"), refs);
            collectPrimaryFileRef(nested, refs);
        }
    }

    private void collectPrimaryFileRef(Map<String, Object> values, List<AcademicToolFileRef> refs) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (!hasPrimaryFilePayload(values)) {
            return;
        }
        AcademicToolFileRef fileRef = AcademicToolFileRef.fromMap(values);
        if (StringUtils.hasText(fileRef.getFileName())
                || StringUtils.hasText(fileRef.getDownloadUrl())
                || StringUtils.hasText(fileRef.getPreviewUrl())) {
            refs.add(fileRef);
        }
    }

    private boolean hasPrimaryFilePayload(Map<String, Object> values) {
        return StringUtils.hasText(firstObjectText(
                values.get("primaryFileName"),
                values.get("fileName"),
                values.get("filename"),
                values.get("displayName"),
                values.get("name")))
                || StringUtils.hasText(firstObjectText(
                values.get("downloadUrl"),
                values.get("ossUrl"),
                values.get("domainUrl"),
                values.get("url"),
                values.get("previewUrl")));
    }

    private AcademicArtifact artifactFromFileRef(AcademicLedgerContext.Context context,
                                                 String toolInvocationId,
                                                 String toolName,
                                                 AcademicToolFileRef fileRef) {
        String fileName = firstText(fileRef.getFileName(), fileNameFromUrl(fileRef.getDownloadUrl()));
        String downloadUrl = firstText(fileRef.getDownloadUrl(), fileRef.getPreviewUrl());
        if (!StringUtils.hasText(fileName) && !StringUtils.hasText(downloadUrl)) {
            return null;
        }
        String artifactId = firstText(fileRef.getArtifactId(), stableArtifactId(context, toolInvocationId, fileName, downloadUrl));
        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setUserId(context.userId());
        artifact.setSessionId(context.sessionId());
        artifact.setRunId(context.runId());
        artifact.setToolInvocationId(safe(toolInvocationId));
        artifact.setSourceType("TOOL");
        artifact.setSourceName(firstText(toolName, "tool"));
        artifact.setArtifactType(artifactType(fileName, fileRef.getContentType()));
        artifact.setTitle(firstText(fileName, artifactId));
        artifact.setContent(firstText(fileName, downloadUrl));
        artifact.setDownloadUrl(downloadUrl);
        artifact.setCreateTime(LocalDateTime.now());
        return artifact;
    }

    private String stableArtifactId(AcademicLedgerContext.Context context,
                                    String toolInvocationId,
                                    String fileName,
                                    String downloadUrl) {
        String seed = context.runId() + ":" + safe(toolInvocationId) + ":" + safe(fileName) + ":" + safe(downloadUrl);
        return "ART" + UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private String artifactType(String fileName, String contentType) {
        String ext = extension(fileName);
        if (StringUtils.hasText(ext)) {
            return ext.toUpperCase();
        }
        String type = safe(contentType).toLowerCase();
        if (type.contains("markdown")) {
            return "MD";
        }
        if (type.contains("json")) {
            return "JSON";
        }
        if (type.contains("html")) {
            return "HTML";
        }
        if (type.contains("image/")) {
            return type.substring(type.indexOf('/') + 1).toUpperCase();
        }
        return StringUtils.hasText(contentType) ? contentType.toUpperCase() : "ARTIFACT";
    }

    private String extension(String fileName) {
        String text = safe(fileName);
        int index = text.lastIndexOf('.');
        return index >= 0 && index + 1 < text.length() ? text.substring(index + 1) : "";
    }

    private String fileNameFromUrl(String url) {
        String text = safe(url);
        int query = text.indexOf('?');
        if (query >= 0) {
            text = text.substring(0, query);
        }
        int slash = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < text.length() ? text.substring(slash + 1) : "";
    }

    private boolean same(String left, String right) {
        return StringUtils.hasText(left) && left.equals(right);
    }

    private String firstObjectText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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















