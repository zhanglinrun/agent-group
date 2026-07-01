package com.linrun.domain.agent.ledger.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentReplayResponse;
import com.linrun.api.dto.AgentRunDetailResponse;
import com.linrun.api.dto.AgentSessionDetailResponse;
import com.linrun.api.dto.AgentDiagnosisReportDTO;
import com.linrun.domain.agent.ledger.adapter.AgentExecutionLedgerRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentLlmInvocation;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentRunPlanFactory;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import com.linrun.domain.agent.runtime.diagnosis.AgentDiagnosisService;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputReader;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputView;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class AgentExecutionLedgerService {

    private static final Logger LOGGER = Logger.getLogger(AgentExecutionLedgerService.class.getName());
    private static final int DEFAULT_RUN_LIMIT = 5;

    private final AgentExecutionLedgerRepository ledgerRepository;
    private final AgentReplayProjector replayProjector;
    private final AgentObservabilityMetrics metrics;
    private final AgentDiagnosisService diagnosisService;
    private final AgentRunPlanFactory runPlanFactory = new AgentRunPlanFactory();
    private final AgentToolOutputReader toolOutputReader = new AgentToolOutputReader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AgentExecutionLedgerService(AgentExecutionLedgerRepository ledgerRepository,
                                          AgentReplayProjector replayProjector,
                                          AgentObservabilityMetrics metrics,
                                          AgentDiagnosisService diagnosisService) {
        this.ledgerRepository = ledgerRepository;
        this.replayProjector = replayProjector;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
        this.diagnosisService = diagnosisService;
    }

    public AgentRun startRun(String userId,
                                     String sessionId,
                                     String projectId,
                                     String requestId,
                                     String taskType,
                                     String question,
                                     String modelName) {
        AgentRun run = new AgentRun();
        run.setRunId(nextId("RUN"));
        run.setUserId(safe(userId));
        run.setSessionId(safe(sessionId));
        run.setProjectId(safe(projectId));
        run.setRequestId(safe(requestId));
        run.setTaskType(safe(taskType));
        run.setQuestion(limit(question, 2048));
        run.setStatus(AgentRun.STATUS_RUNNING);
        run.setModelName(safe(modelName));
        run.setStartedAt(LocalDateTime.now());
        run.setDurationMillis(0L);
        try {
            ledgerRepository.createRun(run);
        } catch (Exception exception) {
            logLedgerFailure("createRun", exception);
        }
        return run;
    }

    public void finishRun(AgentRun run,
                          String status,
                          String summary,
                          String errorCode,
                          String errorMessage,
                          long durationMillis) {
        if (run == null || !StringUtils.hasText(run.getRunId())) {
            return;
        }
        run.setStatus(StringUtils.hasText(status) ? status : AgentRun.STATUS_SUCCESS);
        run.setFinalSummary(limit(summary, 4000));
        run.setErrorCode(safe(errorCode));
        run.setErrorMessage(limit(errorMessage, 1024));
        run.setFinishedAt(LocalDateTime.now());
        run.setDurationMillis(Math.max(0L, durationMillis));
        metrics.recordAgentRun(run.getTaskType(), run.getStatus(), run.getDurationMillis());
        try {
            ledgerRepository.finishRun(run);
        } catch (Exception exception) {
            logLedgerFailure("finishRun", exception);
        }
    }

    public void recordLlmInvocation(AgentLedgerContext.Context context,
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
        AgentLlmInvocation invocation = new AgentLlmInvocation();
        invocation.setInvocationId(nextId("LLM"));
        invocation.setRunId(context.runId());
        invocation.setRequestId(context.requestId());
        invocation.setSessionId(context.sessionId());
        invocation.setUserId(context.userId());
        invocation.setModelName(safe(modelName));
        invocation.setPromptSummary(limit(promptSummary, 2048));
        invocation.setResponseText(limit(responseText, 12000));
        invocation.setStatus(StringUtils.hasText(status) ? status : AgentRun.STATUS_SUCCESS);
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
        metrics.recordLlmCall(invocation.getModelName(), invocation.getStatus(), fallbackUsed, invocation.getLatencyMillis());
        metrics.recordTokenUsage("agent", promptTokens, completionTokens);
        try {
            ledgerRepository.createLlmInvocation(invocation);
        } catch (Exception exception) {
            logLedgerFailure("createLlmInvocation", exception);
        }
    }

    public String recordToolStart(AgentLedgerContext.Context context,
                                  String toolCallId,
                                  String toolName,
                                  String action,
                                  String argumentsJson) {
        if (context == null || !StringUtils.hasText(context.runId())) {
            return "";
        }
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setInvocationId(nextId("TOOL"));
        invocation.setRunId(context.runId());
        invocation.setRequestId(context.requestId());
        invocation.setSessionId(context.sessionId());
        invocation.setUserId(context.userId());
        invocation.setToolCallId(safe(toolCallId));
        invocation.setToolName(safe(toolName));
        invocation.setAction(safe(action));
        invocation.setArgumentsJson(limit(argumentsJson, 6000));
        invocation.setStatus(AgentRun.STATUS_RUNNING);
        invocation.setRetryCount(0);
        invocation.setStartedAt(LocalDateTime.now());
        invocation.setLatencyMillis(0L);
        try {
            ledgerRepository.createToolInvocation(invocation);
            return invocation.getInvocationId();
        } catch (Exception exception) {
            logLedgerFailure("createToolInvocation", exception);
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
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setInvocationId(invocationId);
        invocation.setStatus(StringUtils.hasText(status) ? status : AgentRun.STATUS_SUCCESS);
        invocation.setResultSummary(limit(resultSummary, 1024));
        invocation.setResultJson(limit(resultJson, 12000));
        invocation.setRetryCount(Math.max(0, retryCount));
        invocation.setErrorMessage(limit(errorMessage, 1024));
        invocation.setFinishedAt(LocalDateTime.now());
        invocation.setLatencyMillis(Math.max(0L, latencyMillis));
        try {
            ledgerRepository.finishToolInvocation(invocation);
        } catch (Exception exception) {
            logLedgerFailure("finishToolInvocation", exception);
        }
    }

    public void recordToolArtifacts(AgentLedgerContext.Context context,
                                    String toolInvocationId,
                                    String toolName,
                                    Map<String, Object> result) {
        if (context == null
                || !StringUtils.hasText(context.runId())
                || !StringUtils.hasText(context.userId())
                || !StringUtils.hasText(context.sessionId())) {
            return;
        }
        List<AgentToolFileRef> fileRefs = toolOutputReader.fileRefs(result);
        if (fileRefs.isEmpty()) {
            return;
        }
        for (AgentToolFileRef fileRef : fileRefs) {
            AgentArtifact artifact = artifactFromFileRef(context, toolInvocationId, toolName, fileRef);
            if (artifact == null) {
                continue;
            }
            try {
                ledgerRepository.saveArtifact(artifact);
            } catch (Exception exception) {
                logLedgerFailure("saveArtifact", exception);
            }
        }
    }

    public List<AgentRun> queryRuns(String userId, String sessionId, int limit) {
        return ledgerRepository.queryRuns(userId, sessionId, Math.max(1, Math.min(limit, 50)));
    }

    public AgentRunDetailResponse queryRunDetail(String userId, String runId) {
        AgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        return detail(run);
    }

    public AgentDiagnosisReportDTO queryRunDiagnosis(String userId, String runId) {
        AgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        List<AgentToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
        List<AgentLlmInvocation> llmInvocations = ledgerRepository.queryLlmInvocations(run.getRunId());
        List<AgentArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
        return diagnosis(run, toolInvocations, llmInvocations, artifacts);
    }

    public List<AgentReplayResponse> querySessionReplays(String userId, String sessionId) {
        return queryRuns(userId, sessionId, DEFAULT_RUN_LIMIT).stream()
                .map(this::replay)
                .toList();
    }

    public int deleteSessionRunsSince(String userId, String sessionId, LocalDateTime startedAt) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId) || startedAt == null) {
            return 0;
        }
        try {
            return ledgerRepository.deleteSessionRunsSince(userId, sessionId, startedAt);
        } catch (Exception exception) {
            logLedgerFailure("deleteSessionRunsSince", exception);
            return 0;
        }
    }

    public AgentSessionDetailResponse.MemorySnapshot querySessionMemory(String userId,
                                                                           String sessionId,
                                                                           String currentRequestId,
                                                                           int limit) {
        AgentSessionDetailResponse.MemorySnapshot memory = new AgentSessionDetailResponse.MemorySnapshot();
        memory.setSessionId(safe(sessionId));
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return memory;
        }
        List<AgentRun> latestRuns = ledgerRepository.queryRuns(
                userId, sessionId, Math.max(1, Math.min(limit, 20)));
        List<AgentRun> runs = new ArrayList<>();
        for (int index = latestRuns.size() - 1; index >= 0; index--) {
            AgentRun run = latestRuns.get(index);
            if (same(run.getRequestId(), currentRequestId)) {
                continue;
            }
            runs.add(run);
        }

        List<AgentSessionDetailResponse.ToolObservation> observations = new ArrayList<>();
        Map<String, AgentSessionDetailResponse.Artifact> reusableArtifacts = new LinkedHashMap<>();
        StringBuilder dialogue = new StringBuilder("## Session Memory\n");
        for (AgentRun run : runs) {
            List<AgentToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
            List<AgentArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
            memory.getRuns().add(runMemory(run));
            appendRunDialogue(dialogue, run, toolInvocations, artifacts);
            for (AgentArtifact artifact : artifacts) {
                if (isReusableArtifact(artifact)) {
                    reusableArtifacts.put(artifact.getArtifactId(), artifact(artifact));
                }
            }
            for (AgentToolInvocation invocation : toolInvocations) {
                AgentSessionDetailResponse.ToolObservation observation = toolObservation(invocation, artifacts);
                observations.add(observation);
                for (AgentSessionDetailResponse.Artifact artifact : observation.getArtifactRefs()) {
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

    public AgentReplayResponse queryRunReplay(String userId, String runId) {
        AgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        return replay(run);
    }

    private AgentRunDetailResponse detail(AgentRun run) {
        List<AgentLlmInvocation> llmInvocations = ledgerRepository.queryLlmInvocations(run.getRunId());
        List<AgentToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
        List<AgentArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
        AgentRunDetailResponse response = new AgentRunDetailResponse();
        response.setRun(run(run));
        AgentDiagnosisReportDTO diagnosis = diagnosis(run, toolInvocations, llmInvocations, artifacts);
        response.setDiagnosis(diagnosis);
        response.setEvidence(evidence(run, toolInvocations, llmInvocations, artifacts, diagnosis));
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

    private AgentRunDetailResponse.Evidence evidence(AgentRun run,
                                                        List<AgentToolInvocation> toolInvocations,
                                                        List<AgentLlmInvocation> llmInvocations,
                                                        List<AgentArtifact> artifacts,
                                                        AgentDiagnosisReportDTO diagnosis) {
        List<AgentToolInvocation> tools = safeList(toolInvocations);
        AgentRunDetailResponse.Evidence dto = new AgentRunDetailResponse.Evidence();
        dto.setMode(modeEvidence(run));
        dto.setPlan(planEvidence(run, tools));
        dto.setFailedTools(failedTools(tools));
        dto.setReplanReasons(dto.getFailedTools().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRecoveredByLaterTool()))
                .map(AgentRunDetailResponse.ToolFailure::getReplanReason)
                .filter(StringUtils::hasText)
                .toList());
        dto.setToolCallCount(safeList(toolInvocations).size());
        dto.setFailedToolCount((int) tools.stream().filter(this::isFailedTool).count());
        dto.setReplanCount(estimateReplanCount(tools));
        dto.setLlmCallCount(safeList(llmInvocations).size());
        dto.setArtifactCount(safeList(artifacts).size());
        dto.setQuotaConsumed(quotaConsumed(tools));
        dto.setToolSuccessRate(tools.isEmpty()
                ? 1.0d
                : (double) (tools.size() - dto.getFailedToolCount()) / tools.size());
        if (diagnosis != null) {
            dto.setDiagnosisLevel(diagnosis.getLevel());
            dto.setDiagnosisSummary(diagnosis.getSummary());
        }
        return dto;
    }

    private AgentRunDetailResponse.Mode modeEvidence(AgentRun run) {
        AgentRunDetailResponse.Mode dto = new AgentRunDetailResponse.Mode();
        String taskType = safe(run.getTaskType());
        dto.setTaskType(taskType);
        switch (normalizeTaskType(taskType)) {
            case "deep" -> {
                dto.setExecutionMode("Plan-Execute");
                dto.setModeFamily("plan-execute");
                dto.setAgentType("deep");
                dto.setReason("深度研究任务需要多步骤规划和依赖编排");
            }
            case "ppt" -> {
                dto.setExecutionMode("PPT Workflow");
                dto.setModeFamily("ppt-workflow");
                dto.setAgentType("ppt");
                dto.setReason("PPT 生成按需求澄清、大纲、素材和渲染路线推进");
            }
            case "skill", "skills", "manual-skills", "skill-sop" -> {
                dto.setExecutionMode("Skill Orchestration");
                dto.setModeFamily("skill-orchestration");
                dto.setAgentType("skill");
                dto.setReason("技能任务读取预定义技能并组合工具完成");
            }
            case "image" -> {
                dto.setExecutionMode("ReAct");
                dto.setModeFamily("react");
                dto.setAgentType("image");
                dto.setReason("图像任务通过提示词整理和图像工具调用完成");
            }
            case "trade-diagnosis" -> {
                dto.setExecutionMode("ReAct");
                dto.setModeFamily("react");
                dto.setAgentType("trade-diagnosis");
                dto.setReason("交易诊断任务只读聚合订单、支付、退款和额度流水后给出一致性结论");
            }
            default -> {
                dto.setExecutionMode("ReAct");
                dto.setModeFamily("react");
                dto.setAgentType(normalizeTaskType(taskType));
                dto.setReason("适合文件问答、搜索或普通对话的思考-行动循环");
            }
        }
        return dto;
    }

    private AgentRunDetailResponse.PlanEvidence planEvidence(AgentRun run,
                                                                List<AgentToolInvocation> toolInvocations) {
        AgentPlan plan = runPlanFactory.build(run.getTaskType(), hasSearchTool(toolInvocations));
        AgentRunDetailResponse.PlanEvidence dto = new AgentRunDetailResponse.PlanEvidence();
        dto.setTitle(plan.getTitle());
        dto.setRevisionCount(1 + estimateReplanCount(toolInvocations));
        dto.setSteps(plan.getSteps().stream()
                .map(this::planStep)
                .toList());
        return dto;
    }

    private AgentRunDetailResponse.PlanStep planStep(AgentPlanStep step) {
        AgentRunDetailResponse.PlanStep dto = new AgentRunDetailResponse.PlanStep();
        dto.setStepId(step.getStepId());
        dto.setInstruction(step.getInstruction());
        dto.setOrder(step.getOrder());
        dto.setStatus(step.getStatus());
        dto.setAssignedAgent(step.getAssignedAgent());
        dto.setDependencies(step.getDependencies());
        return dto;
    }

    private List<AgentRunDetailResponse.ToolFailure> failedTools(List<AgentToolInvocation> toolInvocations) {
        List<AgentRunDetailResponse.ToolFailure> result = new ArrayList<>();
        List<AgentToolInvocation> tools = safeList(toolInvocations);
        for (int index = 0; index < tools.size(); index++) {
            AgentToolInvocation invocation = tools.get(index);
            if (!isFailedTool(invocation)) {
                continue;
            }
            boolean recovered = hasLaterSuccessTool(tools, index);
            AgentRunDetailResponse.ToolFailure dto = new AgentRunDetailResponse.ToolFailure();
            dto.setInvocationId(invocation.getInvocationId());
            dto.setToolName(invocation.getToolName());
            dto.setErrorMessage(invocation.getErrorMessage());
            dto.setRecoveredByLaterTool(recovered);
            dto.setReplanReason(recovered ? replanReason(invocation) : "");
            result.add(dto);
        }
        return result;
    }

    private AgentReplayResponse replay(AgentRun run) {
        return replayProjector.project(run,
                ledgerRepository.queryLlmInvocations(run.getRunId()),
                ledgerRepository.queryToolInvocations(run.getRunId()),
                ledgerRepository.queryArtifactsByRun(run.getRunId()));
    }

    private AgentDiagnosisReportDTO diagnosis(AgentRun run,
                                              List<AgentToolInvocation> toolInvocations,
                                              List<AgentLlmInvocation> llmInvocations,
                                              List<AgentArtifact> artifacts) {
        int toolCallCount = safeList(toolInvocations).size();
        int failedToolCount = (int) safeList(toolInvocations).stream()
                .filter(this::isFailedTool)
                .count();
        int replanCount = estimateReplanCount(toolInvocations);
        long elapsedMs = run.getDurationMillis() == null ? 0L : Math.max(0L, run.getDurationMillis());
        double quotaConsumed = quotaConsumed(toolInvocations);
        boolean failed = AgentRun.STATUS_FAILED.equals(safe(run.getStatus()));
        AgentDiagnosisService.DiagnosisReport report = diagnosisService.diagnose(
                new AgentDiagnosisService.AgentRunContext(
                        run.getRunId(),
                        elapsedMs,
                        failedToolCount,
                        quotaConsumed,
                        replanCount,
                        failed,
                        run.getErrorMessage()));

        AgentDiagnosisReportDTO dto = new AgentDiagnosisReportDTO();
        dto.setRunId(run.getRunId());
        dto.setSessionId(run.getSessionId());
        dto.setLevel(report.getLevel().name());
        dto.setSummary(report.getSummary());
        dto.setIssues(report.getIssues().stream()
                .map(item -> new AgentDiagnosisReportDTO.DiagnosisItemDTO(
                        item.getLevel().name(), item.getCode(), item.getMessage()))
                .toList());
        dto.setElapsedMs(elapsedMs);
        dto.setToolCallCount(toolCallCount);
        dto.setFailedToolCount(failedToolCount);
        dto.setQuotaConsumed(quotaConsumed);
        dto.setReplanCount(replanCount);
        dto.setLlmCallCount(safeList(llmInvocations).size());
        dto.setArtifactCount(safeList(artifacts).size());
        dto.setToolSuccessRate(toolCallCount == 0
                ? 1.0d
                : (double) (toolCallCount - failedToolCount) / toolCallCount);
        return dto;
    }

    private boolean isFailedTool(AgentToolInvocation invocation) {
        return invocation != null && AgentRun.STATUS_FAILED.equals(safe(invocation.getStatus()));
    }

    private int estimateReplanCount(List<AgentToolInvocation> toolInvocations) {
        return (int) safeList(toolInvocations).stream()
                .filter(this::isReplanEvent)
                .count();
    }

    private boolean isReplanEvent(AgentToolInvocation invocation) {
        if (invocation == null) {
            return false;
        }
        String toolName = safe(invocation.getToolName()).toLowerCase();
        if (isReplanMarker(toolName)) {
            return true;
        }
        Map<String, Object> result = parseJsonObject(invocation.getResultJson());
        Map<String, Object> metadata = parseNestedObject(result.get("metadata"));
        String marker = firstObjectText(
                result.get("type"),
                result.get("eventType"),
                result.get("event"),
                result.get("status"),
                metadata.get("type"),
                metadata.get("eventType"),
                metadata.get("event"),
                metadata.get("status"));
        return isReplanMarker(marker)
                || booleanValue(result.get("replanned"))
                || booleanValue(metadata.get("replanned"));
    }

    private boolean isReplanMarker(String marker) {
        String normalized = safe(marker).trim().toLowerCase();
        return "replan".equals(normalized)
                || "replanned".equals(normalized)
                || "agent_replan".equals(normalized)
                || "plan_replan".equals(normalized)
                || "type_replanned".equals(normalized);
    }

    private boolean hasLaterSuccessTool(List<AgentToolInvocation> tools, int currentIndex) {
        for (int index = currentIndex + 1; index < tools.size(); index++) {
            if (AgentRun.STATUS_SUCCESS.equals(safe(tools.get(index).getStatus()))) {
                return true;
            }
        }
        return false;
    }

    private double quotaConsumed(List<AgentToolInvocation> toolInvocations) {
        double total = 0.0d;
        for (AgentToolInvocation invocation : safeList(toolInvocations)) {
            if (AgentToolOutputNames.QUOTA_USAGE.equals(safe(invocation.getToolName()))) {
                Map<String, Object> result = parseJsonObject(invocation.getResultJson());
                Map<String, Object> metadata = parseNestedObject(result.get("metadata"));
                double parsed = firstPositiveDouble(
                        metadata.get("estimatedConsumedQuota"),
                        metadata.get("consumedQuota"),
                        metadata.get("quotaConsumed"),
                        result.get("estimatedConsumedQuota"),
                        result.get("consumedQuota"),
                        result.get("quotaConsumed"));
                if (parsed > 0) {
                    total += parsed;
                }
            }
        }
        return total;
    }

    private AgentRunDetailResponse.Run run(AgentRun run) {
        AgentRunDetailResponse.Run dto = new AgentRunDetailResponse.Run();
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

    private AgentSessionDetailResponse.RunMemory runMemory(AgentRun run) {
        AgentSessionDetailResponse.RunMemory dto = new AgentSessionDetailResponse.RunMemory();
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

    private AgentRunDetailResponse.LlmInvocation llm(AgentLlmInvocation invocation) {
        AgentRunDetailResponse.LlmInvocation dto = new AgentRunDetailResponse.LlmInvocation();
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

    private AgentRunDetailResponse.ToolInvocation tool(AgentToolInvocation invocation,
                                                          List<AgentArtifact> artifacts) {
        AgentToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
        AgentRunDetailResponse.ToolInvocation dto = new AgentRunDetailResponse.ToolInvocation();
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

    private AgentSessionDetailResponse.ToolObservation toolObservation(AgentToolInvocation invocation,
                                                                          List<AgentArtifact> artifacts) {
        AgentToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
        AgentSessionDetailResponse.ToolObservation dto = new AgentSessionDetailResponse.ToolObservation();
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

    private AgentSessionDetailResponse.Artifact artifact(AgentArtifact artifact) {
        AgentSessionDetailResponse.Artifact dto = new AgentSessionDetailResponse.Artifact();
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
                                   AgentRun run,
                                   List<AgentToolInvocation> toolInvocations,
                                   List<AgentArtifact> artifacts) {
        dialogue.append("\n### Run ").append(firstText(run.getRequestId(), run.getRunId())).append('\n');
        appendLine(dialogue, "Task", run.getTaskType());
        appendLine(dialogue, "Question", run.getQuestion());
        appendLine(dialogue, "Status", run.getStatus());
        appendLine(dialogue, "Summary", run.getFinalSummary());
        if (toolInvocations == null || toolInvocations.isEmpty()) {
            dialogue.append("Tool Observations: none\n");
        } else {
            dialogue.append("Tool Observations:\n");
            for (AgentToolInvocation invocation : toolInvocations) {
                AgentToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
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

    private String memorySummary(AgentSessionDetailResponse.MemorySnapshot memory) {
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

    private boolean isReusableArtifact(AgentArtifact artifact) {
        if (artifact == null) {
            return false;
        }
        return !"INTERNAL".equalsIgnoreCase(artifact.getArtifactType())
                && !"INTERNAL".equalsIgnoreCase(artifact.getSourceType());
    }

    private String fileName(AgentArtifact artifact) {
        String content = safe(artifact.getContent());
        int slash = Math.max(content.lastIndexOf('/'), content.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < content.length()) {
            return content.substring(slash + 1);
        }
        return StringUtils.hasText(content) ? content : safe(artifact.getTitle());
    }

    private AgentArtifact artifactFromFileRef(AgentLedgerContext.Context context,
                                                 String toolInvocationId,
                                                 String toolName,
                                                 AgentToolFileRef fileRef) {
        String fileName = firstText(fileRef.getFileName(), fileNameFromUrl(fileRef.getDownloadUrl()));
        String downloadUrl = firstText(fileRef.getDownloadUrl(), fileRef.getPreviewUrl());
        if (!StringUtils.hasText(fileName) && !StringUtils.hasText(downloadUrl)) {
            return null;
        }
        String artifactId = firstText(fileRef.getArtifactId(), stableArtifactId(context, toolInvocationId, fileName, downloadUrl));
        AgentArtifact artifact = new AgentArtifact();
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

    private String stableArtifactId(AgentLedgerContext.Context context,
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseNestedObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value instanceof String text) {
            return parseJsonObject(text);
        }
        return Map.of();
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (Exception ignored) {
            return 0.0d;
        }
    }

    private double firstPositiveDouble(Object... values) {
        for (Object value : values) {
            double parsed = doubleValue(value);
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0.0d;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(text(value));
    }

    private boolean hasSearchTool(List<AgentToolInvocation> toolInvocations) {
        for (AgentToolInvocation invocation : safeList(toolInvocations)) {
            String toolName = safe(invocation.getToolName()).toLowerCase();
            if (toolName.contains("search") || toolName.contains("tavily") || toolName.contains("搜索")) {
                return true;
            }
        }
        return false;
    }

    private String replanReason(AgentToolInvocation invocation) {
        String toolName = safe(invocation.getToolName());
        String reason = safe(invocation.getErrorMessage());
        if (StringUtils.hasText(reason)) {
            return StringUtils.hasText(toolName)
                    ? toolName + " 调用失败：" + limit(reason, 200)
                    : limit(reason, 200);
        }
        return StringUtils.hasText(toolName) ? toolName + " 调用失败后已由后续工具恢复" : "工具调用失败后已由后续步骤恢复";
    }

    private String normalizeTaskType(String taskType) {
        String type = safe(taskType).trim().toLowerCase();
        return switch (type) {
            case "paper", "file" -> "file";
            case "ppt", "pptx" -> "ppt";
            case "deep", "deep-research" -> "deep";
            case "image", "image-generation", "workspace-image" -> "image";
            case "trade-diagnosis", "diagnose-trade", "order-diagnosis",
                 "workspace-trade-diagnosis", "workspace-trade", "trade", "trade-flow", "group-trade" ->
                    "trade-diagnosis";
            case "data", "data-qa", "workspace-data", "nl2sql", "table-rag" -> "data";
            case "skills" -> "skills";
            case "manual", "manual-skills", "skills-manual" -> "manual-skills";
            default -> "chat";
        };
    }

    private void logLedgerFailure(String action, Exception exception) {
        LOGGER.log(Level.WARNING, "agent ledger " + action + " failed: " + exception.getMessage(), exception);
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






