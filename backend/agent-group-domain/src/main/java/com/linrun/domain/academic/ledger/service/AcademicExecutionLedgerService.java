package com.linrun.domain.academic.ledger.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AgentDiagnosisReportDTO;
import com.linrun.domain.academic.ledger.adapter.AcademicExecutionLedgerRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentRunPlanFactory;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import com.linrun.domain.academic.runtime.diagnosis.AgentDiagnosisService;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputReader;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputView;
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
public class AcademicExecutionLedgerService {

    private static final Logger LOGGER = Logger.getLogger(AcademicExecutionLedgerService.class.getName());
    private static final int DEFAULT_RUN_LIMIT = 5;

    private final AcademicExecutionLedgerRepository ledgerRepository;
    private final AcademicReplayProjector replayProjector;
    private final AgentObservabilityMetrics metrics;
    private final AgentDiagnosisService diagnosisService;
    private final AcademicAgentRunPlanFactory runPlanFactory = new AcademicAgentRunPlanFactory();
    private final AcademicToolOutputReader toolOutputReader = new AcademicToolOutputReader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AcademicExecutionLedgerService(AcademicExecutionLedgerRepository ledgerRepository,
                                          AcademicReplayProjector replayProjector,
                                          AgentObservabilityMetrics metrics,
                                          AgentDiagnosisService diagnosisService) {
        this.ledgerRepository = ledgerRepository;
        this.replayProjector = replayProjector;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
        this.diagnosisService = diagnosisService;
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
        } catch (Exception exception) {
            logLedgerFailure("createRun", exception);
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
        metrics.recordAgentRun(run.getTaskType(), run.getStatus(), run.getDurationMillis());
        try {
            ledgerRepository.finishRun(run);
        } catch (Exception exception) {
            logLedgerFailure("finishRun", exception);
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
        metrics.recordLlmCall(invocation.getModelName(), invocation.getStatus(), fallbackUsed, invocation.getLatencyMillis());
        metrics.recordTokenUsage("agent", promptTokens, completionTokens);
        try {
            ledgerRepository.createLlmInvocation(invocation);
        } catch (Exception exception) {
            logLedgerFailure("createLlmInvocation", exception);
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
        } catch (Exception exception) {
            logLedgerFailure("finishToolInvocation", exception);
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
        List<AcademicToolFileRef> fileRefs = toolOutputReader.fileRefs(result);
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
            } catch (Exception exception) {
                logLedgerFailure("saveArtifact", exception);
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

    public AgentDiagnosisReportDTO queryRunDiagnosis(String userId, String runId) {
        AcademicAgentRun run = ledgerRepository.queryRun(userId, runId)
                .orElseThrow(() -> new AppException("LEDGER_0001", "运行记录不存在或无权访问"));
        List<AcademicToolInvocation> toolInvocations = ledgerRepository.queryToolInvocations(run.getRunId());
        List<AcademicLlmInvocation> llmInvocations = ledgerRepository.queryLlmInvocations(run.getRunId());
        List<AcademicArtifact> artifacts = ledgerRepository.queryArtifactsByRun(run.getRunId());
        return diagnosis(run, toolInvocations, llmInvocations, artifacts);
    }

    public List<AcademicReplayResponse> querySessionReplays(String userId, String sessionId) {
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

    private AcademicRunDetailResponse.Evidence evidence(AcademicAgentRun run,
                                                        List<AcademicToolInvocation> toolInvocations,
                                                        List<AcademicLlmInvocation> llmInvocations,
                                                        List<AcademicArtifact> artifacts,
                                                        AgentDiagnosisReportDTO diagnosis) {
        List<AcademicToolInvocation> tools = safeList(toolInvocations);
        AcademicRunDetailResponse.Evidence dto = new AcademicRunDetailResponse.Evidence();
        dto.setMode(modeEvidence(run));
        dto.setPlan(planEvidence(run, tools));
        dto.setFailedTools(failedTools(tools));
        dto.setReplanReasons(dto.getFailedTools().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRecoveredByLaterTool()))
                .map(AcademicRunDetailResponse.ToolFailure::getReplanReason)
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

    private AcademicRunDetailResponse.Mode modeEvidence(AcademicAgentRun run) {
        AcademicRunDetailResponse.Mode dto = new AcademicRunDetailResponse.Mode();
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
                dto.setReason("适合文件问答、搜索或普通学术问答的思考-行动循环");
            }
        }
        return dto;
    }

    private AcademicRunDetailResponse.PlanEvidence planEvidence(AcademicAgentRun run,
                                                                List<AcademicToolInvocation> toolInvocations) {
        AcademicAgentPlan plan = runPlanFactory.build(run.getTaskType(), hasSearchTool(toolInvocations));
        AcademicRunDetailResponse.PlanEvidence dto = new AcademicRunDetailResponse.PlanEvidence();
        dto.setTitle(plan.getTitle());
        dto.setRevisionCount(1 + estimateReplanCount(toolInvocations));
        dto.setSteps(plan.getSteps().stream()
                .map(this::planStep)
                .toList());
        return dto;
    }

    private AcademicRunDetailResponse.PlanStep planStep(AcademicPlanStep step) {
        AcademicRunDetailResponse.PlanStep dto = new AcademicRunDetailResponse.PlanStep();
        dto.setStepId(step.getStepId());
        dto.setInstruction(step.getInstruction());
        dto.setOrder(step.getOrder());
        dto.setStatus(step.getStatus());
        dto.setAssignedAgent(step.getAssignedAgent());
        dto.setDependencies(step.getDependencies());
        return dto;
    }

    private List<AcademicRunDetailResponse.ToolFailure> failedTools(List<AcademicToolInvocation> toolInvocations) {
        List<AcademicRunDetailResponse.ToolFailure> result = new ArrayList<>();
        List<AcademicToolInvocation> tools = safeList(toolInvocations);
        for (int index = 0; index < tools.size(); index++) {
            AcademicToolInvocation invocation = tools.get(index);
            if (!isFailedTool(invocation)) {
                continue;
            }
            boolean recovered = hasLaterSuccessTool(tools, index);
            AcademicRunDetailResponse.ToolFailure dto = new AcademicRunDetailResponse.ToolFailure();
            dto.setInvocationId(invocation.getInvocationId());
            dto.setToolName(invocation.getToolName());
            dto.setErrorMessage(invocation.getErrorMessage());
            dto.setRecoveredByLaterTool(recovered);
            dto.setReplanReason(recovered ? replanReason(invocation) : "");
            result.add(dto);
        }
        return result;
    }

    private AcademicReplayResponse replay(AcademicAgentRun run) {
        return replayProjector.project(run,
                ledgerRepository.queryLlmInvocations(run.getRunId()),
                ledgerRepository.queryToolInvocations(run.getRunId()),
                ledgerRepository.queryArtifactsByRun(run.getRunId()));
    }

    private AgentDiagnosisReportDTO diagnosis(AcademicAgentRun run,
                                              List<AcademicToolInvocation> toolInvocations,
                                              List<AcademicLlmInvocation> llmInvocations,
                                              List<AcademicArtifact> artifacts) {
        int toolCallCount = safeList(toolInvocations).size();
        int failedToolCount = (int) safeList(toolInvocations).stream()
                .filter(this::isFailedTool)
                .count();
        int replanCount = estimateReplanCount(toolInvocations);
        long elapsedMs = run.getDurationMillis() == null ? 0L : Math.max(0L, run.getDurationMillis());
        double quotaConsumed = quotaConsumed(toolInvocations);
        boolean failed = AcademicAgentRun.STATUS_FAILED.equals(safe(run.getStatus()));
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

    private boolean isFailedTool(AcademicToolInvocation invocation) {
        return invocation != null && AcademicAgentRun.STATUS_FAILED.equals(safe(invocation.getStatus()));
    }

    private int estimateReplanCount(List<AcademicToolInvocation> toolInvocations) {
        return (int) safeList(toolInvocations).stream()
                .filter(this::isReplanEvent)
                .count();
    }

    private boolean isReplanEvent(AcademicToolInvocation invocation) {
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

    private boolean hasLaterSuccessTool(List<AcademicToolInvocation> tools, int currentIndex) {
        for (int index = currentIndex + 1; index < tools.size(); index++) {
            if (AcademicAgentRun.STATUS_SUCCESS.equals(safe(tools.get(index).getStatus()))) {
                return true;
            }
        }
        return false;
    }

    private double quotaConsumed(List<AcademicToolInvocation> toolInvocations) {
        double total = 0.0d;
        for (AcademicToolInvocation invocation : safeList(toolInvocations)) {
            if (AcademicToolOutputNames.QUOTA_USAGE.equals(safe(invocation.getToolName()))) {
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

    private boolean hasSearchTool(List<AcademicToolInvocation> toolInvocations) {
        for (AcademicToolInvocation invocation : safeList(toolInvocations)) {
            String toolName = safe(invocation.getToolName()).toLowerCase();
            if (toolName.contains("search") || toolName.contains("tavily") || toolName.contains("搜索")) {
                return true;
            }
        }
        return false;
    }

    private String replanReason(AcademicToolInvocation invocation) {
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
        LOGGER.log(Level.WARNING, "academic ledger " + action + " failed: " + exception.getMessage(), exception);
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






