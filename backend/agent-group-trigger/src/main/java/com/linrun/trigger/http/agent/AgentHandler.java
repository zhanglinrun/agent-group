package com.linrun.trigger.http.agent;

import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.linrun.trigger.http.agent.support.AgentJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentStreamRequest;
import com.linrun.api.dto.AgentFileUploadResponse;
import com.linrun.api.dto.AgentReplayResponse;
import com.linrun.api.dto.AgentRunDetailResponse;
import com.linrun.api.dto.AgentSessionDetailResponse;
import com.linrun.api.dto.AgentSessionSummaryDTO;
import com.linrun.api.dto.AgentDiagnosisReportDTO;
import com.linrun.api.dto.QuotaStreamEvent;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.ledger.service.AgentLedgerContext;
import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.domain.agent.memory.service.UserAgentMemoryService;
import com.linrun.domain.agent.workspace.service.AgentWorkspaceService;
import com.linrun.domain.agent.runtime.agent.AgentFlowProjector;
import com.linrun.domain.agent.runtime.agent.AgentFlowStage;
import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentFlowProgress;
import com.linrun.domain.agent.runtime.agent.AgentFlowProgressProjector;
import com.linrun.domain.agent.runtime.agent.AgentFlowProgressResult;
import com.linrun.domain.agent.runtime.agent.AgentRunPlanFactory;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import com.linrun.domain.agent.runtime.diagnosis.AgentDiagnosisService;
import com.linrun.domain.agent.runtime.security.PromptInjectionGuard;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentHandler {

    private final AgentNativeService agentNativeService;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AgentTaskManager taskManager;
    private final AiPptInstService aiPptInstService;
    private final AgentBackgroundStreamService backgroundStreamService;
    private final AgentArtifactService agentArtifactService;
    private final AgentExecutionLedgerService agentExecutionLedgerService;
    private final AgentWorkspaceService agentWorkspaceService;
    private final UserAgentMemoryService userAgentMemoryService;
    private final ObjectMapper objectMapper;
    private final AgentJsonCodec jsonCodec;
    private final AgentDiagnosisService diagnosisService;
    private final AgentRunPlanFactory runPlanFactory = new AgentRunPlanFactory();
    private final AgentFlowProjector flowProjector = new AgentFlowProjector();
    private final AgentFlowProgressProjector flowProgressProjector = new AgentFlowProgressProjector();
    private final UnifiedAgentOrchestrator unifiedAgentOrchestrator = new UnifiedAgentOrchestrator();

    @Autowired
    public AgentHandler(AgentNativeService agentNativeService,
                                    UserAccountService userAccountService,
                                    UserQuotaService userQuotaService,
                                    AgentTaskManager taskManager,
                                    AiPptInstService aiPptInstService,
                                    AgentBackgroundStreamService backgroundStreamService,
                                    AgentArtifactService agentArtifactService,
                                    AgentExecutionLedgerService agentExecutionLedgerService,
                                    AgentWorkspaceService agentWorkspaceService,
                                    UserAgentMemoryService userAgentMemoryService,
                                    ObjectMapper objectMapper,
                                    AgentJsonCodec jsonCodec,
                                    AgentDiagnosisService diagnosisService) {
        this.agentNativeService = agentNativeService;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.taskManager = taskManager;
        this.aiPptInstService = aiPptInstService;
        this.backgroundStreamService = backgroundStreamService;
        this.agentArtifactService = agentArtifactService;
        this.agentExecutionLedgerService = agentExecutionLedgerService;
        this.agentWorkspaceService = agentWorkspaceService;
        this.userAgentMemoryService = userAgentMemoryService;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.diagnosisService = diagnosisService;
    }

    public Flux<QuotaStreamEvent<?>> backgroundStreamEventFlux(String token,
                                                               AgentStreamRequest request,
                                                               String sessionId,
                                                               String requestId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String taskKey = internalSessionId(user.getUserId(), sessionId);
        return backgroundStreamService.startOrAttach(taskKey,
                () -> streamEventFlux(token, request, sessionId, requestId));
    }

    public Flux<QuotaStreamEvent<?>> attachEventFlux(String token,
                                                     String sessionId,
                                                     String requestId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String taskKey = internalSessionId(user.getUserId(), sessionId);
        return backgroundStreamService.attach(taskKey)
                .switchIfEmpty(Flux.just(event("done", sessionId, requestId, new AtomicInteger(1), "done")));
    }

    public Map<String, Object> capabilities() {
        return agentNativeService.capabilities();
    }

    public Flux<QuotaStreamEvent<?>> streamEventFlux(String token,
                                                     AgentStreamRequest request,
                                                     String sessionId,
                                                     String requestId) {
        return Flux.defer(() -> {
            UserAccount user = userAccountService.requireUserByToken(token);
            AgentStreamRequest safeRequest = request == null ? new AgentStreamRequest() : request;
            String requestedTaskType = normalizeTaskType(safeRequest.getTaskType());
            String preliminaryQuery = normalizeQuery(safeRequest,
                    UnifiedAgentOrchestrator.AUTO_TASK_TYPE.equals(requestedTaskType) ? "chat" : requestedTaskType);
            String fileId = effectiveFileIds(safeRequest);
            String projectId = nullToBlank(safeRequest.getProjectId());
            boolean webSearchEnabled = Boolean.TRUE.equals(safeRequest.getWebSearchEnabled());
            UnifiedAgentOrchestrator.OrchestrationPlan orchestrationPlan =
                    unifiedAgentOrchestrator.plan(preliminaryQuery, requestedTaskType, fileId, webSearchEnabled, safeRequest);
            String executionAgentType = UnifiedAgentOrchestrator.resolveExecutionAgentType(requestedTaskType, orchestrationPlan);
            String query = normalizeQuery(safeRequest, executionAgentType);
            long startedAt = System.currentTimeMillis();
            AtomicInteger sequence = new AtomicInteger(1);
            String modelName = modelName(user.getUserId(), safeRequest);
            boolean customModelConfigured = userQuotaService.hasEnabledModelConfig(user.getUserId())
                    || hasCustomModelConfig(safeRequest);
            AgentRun run = agentExecutionLedgerService.startRun(
                    user.getUserId(), sessionId, projectId, requestId, executionAgentType, query, modelName);
            String executionMemoryPrompt = joinPrompts(
                    outputStylePrompt(effectiveOutputStyle(executionAgentType, safeRequest.getOutputStyle())),
                    executionMemoryPrompt(user.getUserId(), sessionId, requestId)
            );
            AgentLedgerContext.Context ledgerContext = new AgentLedgerContext.Context(
                    run.getRunId(), requestId, sessionId, user.getUserId(), executionAgentType);
            AgentPlan executionPlan = runPlanFactory.build(executionAgentType, webSearchEnabled);
            RunState runState = new RunState(run, ledgerContext, query, modelName, startedAt,
                    webSearchEnabled, executionPlan, orchestrationPlan, requestedTaskType, executionAgentType);
            runState.projectId = projectId;
            runState.fileId = fileId;
            runState.projectContext = projectContext(user.getUserId(), projectId);

            boolean identityQuestion = isModelIdentityQuestion(query);
            Flux<QuotaStreamEvent<?>> executionEvents = identityQuestion
                    ? Flux.defer(() -> Flux.fromIterable(identityAnswerEvents(
                            token, executionAgentType, query, sessionId, fileId, requestId, sequence, runState)))
                    : Flux.defer(() -> agentNativeService.stream(token, executionAgentType, query, sessionId, fileId,
                            webSearchEnabled, safeRequest.getLlmBaseUrl(), safeRequest.getLlmApiKey(),
                            safeRequest.getLlmModel(), executionMemoryPrompt, safeRequest.getContinueTraceId()))
                            .doOnSubscribe(subscription -> AgentLedgerContext.set(ledgerContext))
                            .flatMapIterable(raw -> toEvents(raw, sessionId, requestId, sequence, runState));

            return Flux.concat(
                            Flux.fromIterable(startEvents(runState, sessionId, requestId, sequence)),
                            executionEvents.concatWith(Flux.defer(() -> Flux.fromIterable(completionEvents(
                                    user, sessionId, requestId, sequence, executionAgentType, startedAt, runState)))))
                    .onErrorResume(error -> Flux.fromIterable(errorEvents(
                            sessionId, requestId, sequence, error, customModelConfigured, runState)))
                    .doFinally(signalType -> AgentLedgerContext.clear());
        });
    }

    public AgentStreamRequest resumeRequest(String token, String sessionId) {
        List<AiSession> messages = agentNativeService.querySessionMessages(token, sessionId);
        AiSession latest = messages.stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AppException("SESSION_0001", "会话不存在，无法继续生成"));
        AgentStreamRequest request = new AgentStreamRequest();
        request.setSessionId(sessionId);
        request.setTaskType(toFrontendTaskType(latest.getAgentType()));
        request.setFileId(nullToBlank(latest.getFileid()));
        request.setQuestion("请从上次停止处继续完成这个任务，避免重复已经完成的内容。");
        return request;
    }

    public Map<String, Object> queryTaskStatus(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String internalSessionId = internalSessionId(user.getUserId(), sessionId);
        List<AiSession> messages = agentNativeService.querySessionMessages(token, sessionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        boolean running = taskManager.hasRunningTask(internalSessionId)
                || backgroundStreamService.isRunning(internalSessionId);
        data.put("running", running);
        data.put("stopped", false);
        data.put("exists", !messages.isEmpty());
        data.put("messageCount", messages.size());
        data.put("resumable", !messages.isEmpty() && !running);
        return data;
    }

    public AgentFileUploadResponse upload(String token, MultipartFile file, String sessionId) {
        FileInfo fileInfo = agentNativeService.upload(token, file, sessionId);
        AgentFileUploadResponse response = new AgentFileUploadResponse();
        response.setFileId(fileInfo.getFileId());
        response.setFileName(fileInfo.getFileName());
        response.setFileType(fileInfo.getFileType());
        response.setFileSize(fileInfo.getFileSize());
        response.setSummary(limit(fileInfo.getExtractedText(), 500));
        response.setStatus(fileInfo.getStatus() == null ? "" : fileInfo.getStatus().name());
        return response;
    }

    public boolean stop(String token, String sessionId) {
        return agentNativeService.stop(token, sessionId);
    }

    public void deleteSession(String token, String sessionId) {
        agentNativeService.deleteSession(token, sessionId);
    }

    public boolean rollbackSession(String token, String sessionId, String messageId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        LocalDateTime anchorTime = agentNativeService.rollbackSessionFromMessage(token, sessionId, messageId);
        if (anchorTime == null) {
            return false;
        }
        agentExecutionLedgerService.deleteSessionRunsSince(user.getUserId(), sessionId, anchorTime);
        agentArtifactService.clearManifest(user.getUserId(), sessionId);
        return true;
    }

    public List<AgentSessionSummaryDTO> querySessions(String token, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentNativeService.querySessions(token, 1, Math.max(1, Math.min(limit, 100)))
                .stream()
                .map(session -> toSummary(user, session))
                .toList();
    }

    public AgentSessionDetailResponse queryDetail(String token, String sessionId) {
        AgentSessionDetailResponse response = new AgentSessionDetailResponse();
        response.setSessionId(sessionId);
        List<AgentSessionDetailResponse.Message> messages = new ArrayList<>();
        String lastAssistantAnswer = "";
        for (AiSession session : agentNativeService.querySessionMessages(token, sessionId)) {
            if (StringUtils.hasText(session.getQuestion())) {
                messages.add(toMessage(String.valueOf(session.getId()), "USER", session.getQuestion(), session.getCreateTime()));
            }
            if (StringUtils.hasText(session.getAnswer())) {
                lastAssistantAnswer = session.getAnswer();
                AgentSessionDetailResponse.Message assistantMessage =
                        toMessage(String.valueOf(session.getId()), "ASSISTANT", agentArtifactService.sanitizeLocalPaths(session.getAnswer()), session.getUpdateTime());
                assistantMessage.setReferences(parseReferences(session.getReference()));
                assistantMessage.setRecommend(parseRecommend(session.getRecommend()));
                messages.add(assistantMessage);
            }
        }
        UserAccount user = userAccountService.requireUserByToken(token);
        List<AgentSessionDetailResponse.Artifact> artifacts =
                agentArtifactService.loadManifest(user.getUserId(), sessionId);
        if (artifacts.isEmpty()) {
            artifacts = agentArtifactService.collectFromAnswerAndSave(user.getUserId(), sessionId, lastAssistantAnswer);
        }
        if (!artifacts.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                AgentSessionDetailResponse.Message message = messages.get(i);
                if ("ASSISTANT".equals(message.getRole())) {
                    message.setArtifacts(artifacts);
                    break;
                }
            }
        }
        appendFailureAssistantIfNeeded(user.getUserId(), sessionId, messages);
        try {
            response.setReplays(agentExecutionLedgerService.querySessionReplays(user.getUserId(), sessionId));
        } catch (Exception ignored) {
            response.setReplays(List.of());
        }
        try {
            response.setMemory(agentExecutionLedgerService.querySessionMemory(user.getUserId(), sessionId, "", 8));
        } catch (Exception ignored) {
            response.setMemory(new AgentSessionDetailResponse.MemorySnapshot());
        }
        response.setMessages(messages);
        return response;
    }

    public List<AgentRunDetailResponse.Run> queryRuns(String token, String sessionId, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentExecutionLedgerService.queryRuns(user.getUserId(), sessionId, limit).stream()
                .map(this::toRun)
                .toList();
    }

    public AgentRunDetailResponse queryRunDetail(String token, String runId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentExecutionLedgerService.queryRunDetail(user.getUserId(), runId);
    }

    public AgentDiagnosisReportDTO queryRunDiagnosis(String token, String runId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentExecutionLedgerService.queryRunDiagnosis(user.getUserId(), runId);
    }

    public List<AgentReplayResponse> queryReplay(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentExecutionLedgerService.querySessionReplays(user.getUserId(), sessionId);
    }

    public AgentReplayResponse queryRunReplay(String token, String runId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return agentExecutionLedgerService.queryRunReplay(user.getUserId(), runId);
    }

    public AgentArtifactService.DownloadArtifact downloadArtifact(String token,
                                                                     String sessionId,
                                                                     String artifactId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        List<AiSession> messages = agentNativeService.querySessionMessages(token, sessionId);
        if (messages.isEmpty()) {
            throw new AppException("ARTIFACT_0004", "会话不存在或无权访问");
        }
        List<AgentSessionDetailResponse.Artifact> artifacts =
                agentArtifactService.loadManifest(user.getUserId(), sessionId);
        boolean allowed = artifacts.stream().anyMatch(artifact -> artifactId.equals(artifact.getArtifactId()));
        if (!allowed) {
            throw new AppException("ARTIFACT_0004", "会话不存在或无权访问");
        }
        return agentArtifactService.resolveDownload(artifactId);
    }

    private List<QuotaStreamEvent<?>> toEvents(String raw,
                                               String sessionId,
                                               String requestId,
                                               AtomicInteger sequence,
                                               RunState runState) {
        if (!StringUtils.hasText(raw) || "[DONE]".equals(raw.trim())) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            String type = text(node, "type");
            return switch (type) {
                case "text" -> answerEvents(node, sessionId, requestId, sequence, runState);
                case "thinking" -> List.of(event("task_status", sessionId, requestId, sequence,
                        status("THINKING", content(node))));
                case "reasoning" -> List.of(event("task_status", sessionId, requestId, sequence,
                        status("REASONING", content(node))));
                case "reflection" -> reflectionEvents(node, sessionId, requestId, sequence, runState);
                case "memory_loaded", "context_loaded", "skill_loaded", "capability_loaded" ->
                        capabilityRuntimeEvents(node, sessionId, requestId, sequence, runState);
                case "capability_called" -> capabilityCalledEvents(node, sessionId, requestId, sequence, runState);
                case "tool_start" -> toolStartEvents(node, sessionId, requestId, sequence, runState);
                case "tool_end" -> toolEndEvents(node, sessionId, requestId, sequence, runState);
                case "ppt_status" -> pptStatusEvents(node, sessionId, requestId, sequence, runState);
                case "plan_update" -> planUpdateEvents(node, sessionId, requestId, sequence, runState);
                case "replan", "replanned" -> replanEvents(node, sessionId, requestId, sequence, runState);
                case "reference" -> referenceEvents(node, sessionId, requestId, sequence);
                case "recommend" -> List.of(event("recommend_delta", sessionId, requestId, sequence, recommend(node)));
                case "checkpoint" -> List.of(event("checkpoint", sessionId, requestId, sequence, checkpoint(node)));
                case "error" -> List.of(event("error", sessionId, requestId, sequence,
                        error(text(node, "code"), firstText(node, "message", "content", "detail"))));
                case "complete" -> List.of();
                default -> rawAnswerEvent(raw, sessionId, requestId, sequence, runState);
            };
        } catch (Exception e) {
            return rawAnswerEvent(raw, sessionId, requestId, sequence, runState);
        }
    }

    private List<QuotaStreamEvent<?>> startEvents(RunState runState,
                                                  String sessionId,
                                                  String requestId,
                                                  AtomicInteger sequence) {
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        if (runState.orchestrationPlan != null) {
            events.add(event("task_analysis", sessionId, requestId, sequence,
                    runState.orchestrationPlan.taskAnalysisData(runState.run.getRunId())));
            events.add(event("mode_selection", sessionId, requestId, sequence,
                    runState.orchestrationPlan.modeSelectionData(runState.run.getRunId())));
            events.add(event("agent_routing", sessionId, requestId, sequence,
                    runState.orchestrationPlan.routingData(runState.run.getRunId())));
            events.add(event("execution_applied", sessionId, requestId, sequence,
                    UnifiedAgentOrchestrator.executionAppliedData(
                            runState.run.getRunId(),
                            runState.requestedTaskType,
                            runState.executionAgentType,
                            runState.orchestrationPlan)));
        }
        events.add(event("run_start", sessionId, requestId, sequence, runStart(runState)));
        events.add(event("capability_plan", sessionId, requestId, sequence, capabilityPlan(runState)));
        if (!runState.projectContext.isEmpty()) {
            events.add(event("project_context", sessionId, requestId, sequence, runState.projectContext));
        }
        events.add(event("plan_delta", sessionId, requestId, sequence, plan(runState)));
        AgentFlowProgressResult progress = flowProgressProjector.start(runState.executionPlan);
        runState.currentFlowStageIndex = progress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        return events;
    }

    private List<QuotaStreamEvent<?>> capabilityRuntimeEvents(JsonNode node,
                                                              String sessionId,
                                                              String requestId,
                                                              AtomicInteger sequence,
                                                              RunState runState) {
        String type = text(node, "type");
        Map<String, Object> data = parseObject(node.toString());
        data.put("runId", runState.run.getRunId());
        if ("memory_loaded".equals(type)) {
            Map<String, Object> memory = objectValue(data.get("memory"));
            runState.memoryLoaded = true;
            runState.shortTermMemoryCount = intValue(memory.get("shortTermCount"), runState.shortTermMemoryCount);
            runState.taskMemoryCount = intValue(memory.get("taskMemoryCount"), runState.taskMemoryCount);
            runState.longTermMemoryCount = intValue(memory.get("longTermCount"), runState.longTermMemoryCount);
        } else if ("skill_loaded".equals(type)) {
            runState.skillLoaded = true;
            runState.skillCount = intValue(data.get("skillCount"), runState.skillCount);
        } else if ("capability_loaded".equals(type)) {
            Map<String, Object> capability = objectValue(data.get("capability"));
            runState.capabilityCount = intValue(capability.get("capabilityCount"), runState.capabilityCount);
        }
        return List.of(event(type, sessionId, requestId, sequence, data));
    }

    private List<QuotaStreamEvent<?>> capabilityCalledEvents(JsonNode node,
                                                             String sessionId,
                                                             String requestId,
                                                             AtomicInteger sequence,
                                                             RunState runState) {
        String capabilityName = firstText(node, "capabilityName", "name", "toolName");
        Map<String, Object> data = parseObject(node.toString());
        data.put("runId", runState.run.getRunId());
        runState.capabilityCallCount++;
        if (StringUtils.hasText(capabilityName)) {
            runState.calledCapabilities.put(capabilityName, runState.calledCapabilities.getOrDefault(capabilityName, 0) + 1);
        }
        return List.of(
                event("task_status", sessionId, requestId, sequence,
                        status("CAPABILITY", "调用能力：" + nullToBlank(capabilityName))),
                event("capability_called", sessionId, requestId, sequence, data)
        );
    }

    private List<QuotaStreamEvent<?>> reflectionEvents(JsonNode node,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       RunState runState) {
        boolean passed = booleanValue(node, "passed", false);
        String feedback = firstText(node, "feedback", "message", "content", "detail");
        String action = firstText(node, "action");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("round", integer(node, "round", 1));
        data.put("passed", passed);
        data.put("feedback", feedback);
        data.put("action", StringUtils.hasText(action) ? action : (passed ? "summarize" : "replan"));
        String message = passed ? "反思评估通过，准备生成最终报告" : "反思评估建议调整计划";
        if (StringUtils.hasText(feedback)) {
            message = message + "：" + feedback;
        }
        return List.of(
                event("task_status", sessionId, requestId, sequence, status("REFLECTION", message)),
                event("reflection_delta", sessionId, requestId, sequence, data)
        );
    }

    private List<QuotaStreamEvent<?>> replanEvents(JsonNode node,
                                                   String sessionId,
                                                   String requestId,
                                                   AtomicInteger sequence,
                                                   RunState runState) {
        return planChangeEvents(node, sessionId, requestId, sequence, runState, true);
    }

    private List<QuotaStreamEvent<?>> planUpdateEvents(JsonNode node,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       RunState runState) {
        return planChangeEvents(node, sessionId, requestId, sequence, runState, false);
    }

    private List<QuotaStreamEvent<?>> planChangeEvents(JsonNode node,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       RunState runState,
                                                       boolean replanned) {
        AgentPlan previousPlan = runState.executionPlan;
        AgentPlan replannedPlan = replannedPlan(node, runState.executionPlan);
        String reason = firstText(node, "reason", "message", "content", "detail");
        String prefix = replanned ? "计划已重规划" : "计划已更新";
        String message = StringUtils.hasText(reason) ? prefix + "：" + reason : prefix;
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        events.add(event("task_status", sessionId, requestId, sequence, status(replanned ? "REPLAN" : "PLAN", message)));
        if (replanned) {
            runState.replanCount++;
            events.add(event("replan_delta", sessionId, requestId, sequence,
                    replan(previousPlan, replannedPlan, reason, runState.run.getRunId())));
            AgentFlowProgressResult progress = flowProgressProjector.markReplanned(
                    previousPlan, runState.currentFlowStageIndex, message);
            events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        }
        runState.executionPlan = replannedPlan;
        runState.currentFlowStageIndex = -1;
        events.add(event("plan_delta", sessionId, requestId, sequence, plan(runState)));
        AgentFlowProgressResult progress = flowProgressProjector.start(replannedPlan, message);
        runState.currentFlowStageIndex = progress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        return events;
    }

    private List<QuotaStreamEvent<?>> answerEvents(JsonNode node,
                                                   String sessionId,
                                                   String requestId,
                                                   AtomicInteger sequence,
                                                   RunState runState) {
        String content = agentArtifactService.sanitizeLocalPaths(content(node));
        runState.answer.append(content);
        return List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", content)));
    }

    private List<QuotaStreamEvent<?>> rawAnswerEvent(String raw,
                                                     String sessionId,
                                                     String requestId,
                                                     AtomicInteger sequence,
                                                     RunState runState) {
        String content = agentArtifactService.sanitizeLocalPaths(raw);
        runState.answer.append(content);
        return List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", content)));
    }

    private List<QuotaStreamEvent<?>> identityAnswerEvents(String token,
                                                           String taskType,
                                                           String query,
                                                           String sessionId,
                                                           String fileId,
                                                           String requestId,
                                                           AtomicInteger sequence,
                                                           RunState runState) {
        String answer = identityAnswer(runState.modelName);
        runState.answer.append(answer);
        agentNativeService.saveDeterministicTurn(token, taskType, query, sessionId, fileId,
                answer, Math.max(0L, System.currentTimeMillis() - runState.startedAt));
        return List.of(
                event("task_status", sessionId, requestId, sequence,
                        status("IDENTITY", "已按工作台身份规则回答")),
                event("answer_delta", sessionId, requestId, sequence, Map.of("content", answer))
        );
    }

    private List<QuotaStreamEvent<?>> toolStartEvents(JsonNode node,
                                                      String sessionId,
                                                      String requestId,
                                                      AtomicInteger sequence,
                                                      RunState runState) {
        String toolName = firstText(node, "toolName", "name", "tool");
        String toolCallId = firstText(node, "toolCallId", "tool_call_id", "id");
        String action = firstText(node, "action", "stage");
        String argumentsJson = jsonOrText(node, "arguments", "args", "input", "content");
        String invocationId = agentExecutionLedgerService.recordToolStart(
                runState.ledgerContext, toolCallId, toolName, action, argumentsJson);
        runState.toolInvocations.put(toolKey(toolCallId, toolName), invocationId);
        runState.toolCallCount++;
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        AgentFlowProgressResult progress = flowProgressProjector.advanceToTool(
                runState.executionPlan, runState.currentFlowStageIndex, toolName);
        runState.currentFlowStageIndex = progress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        events.add(event("task_status", sessionId, requestId, sequence,
                status("TOOL", "开始调用工具：" + nullToBlank(toolName))));
        events.add(event("tool_call", sessionId, requestId, sequence,
                toolCall(runState.run.getRunId(), invocationId, toolCallId, toolName, action, argumentsJson)));
        return events;
    }

    private List<QuotaStreamEvent<?>> toolEndEvents(JsonNode node,
                                                    String sessionId,
                                                    String requestId,
                                                    AtomicInteger sequence,
                                                    RunState runState) {
        String toolName = firstText(node, "toolName", "name", "tool");
        String toolCallId = firstText(node, "toolCallId", "tool_call_id", "id");
        String invocationId = runState.toolInvocations.getOrDefault(toolKey(toolCallId, toolName), "");
        String resultText = jsonOrText(node, "result", "output", "content", "detail");
        String rawStatus = firstText(node, "status", "state");
        Map<String, Object> structuredOutput = parseObject(resultText);
        String status = isFailureStatus(rawStatus) || structuredOutput.containsKey("error")
                ? AgentRun.STATUS_FAILED
                : AgentRun.STATUS_SUCCESS;
        if (AgentRun.STATUS_FAILED.equals(status)) {
            runState.failedToolCount++;
        }
        String errorMessage = AgentRun.STATUS_FAILED.equals(status)
                ? firstText(node, "message", "error", "detail",
                Objects.toString(structuredOutput.getOrDefault("error", ""), ""))
                : "";
        long latencyMillis = longValue(node, "latencyMillis", 0L);
        agentExecutionLedgerService.recordToolFinish(invocationId, status,
                limit(resultText, 1024), resultText, integer(node, "retryCount", 0), errorMessage, latencyMillis);
        if (AgentRun.STATUS_SUCCESS.equals(status) && !structuredOutput.isEmpty()) {
            agentExecutionLedgerService.recordToolArtifacts(
                    runState.ledgerContext, invocationId, toolName, structuredOutput);
        }
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        events.add(event("task_status", sessionId, requestId, sequence,
                status("TOOL", "工具调用完成：" + nullToBlank(toolName))));
        events.add(event("tool_result", sessionId, requestId, sequence,
                toolResult(invocationId, toolCallId, toolName, status, resultText, structuredOutput, errorMessage, latencyMillis)));
        return events;
    }

    private List<QuotaStreamEvent<?>> pptStatusEvents(JsonNode node,
                                                      String sessionId,
                                                      String requestId,
                                                      AtomicInteger sequence,
                                                      RunState runState) {
        String stage = firstText(node, "stage", "status");
        String message = firstText(node, "message", "content", "detail");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("stage", StringUtils.hasText(stage) ? stage : "PPT");
        data.put("message", message);
        data.put("pptInstId", firstText(node, "pptInstId", "instId"));
        data.put("pptStatus", firstText(node, "pptStatus", "status"));
        data.put("fileUrl", firstText(node, "fileUrl", "downloadUrl"));
        return List.of(
                event("task_status", sessionId, requestId, sequence,
                        status("PPT", StringUtils.hasText(message) ? message : "PPT流程更新")),
                event("ppt_status", sessionId, requestId, sequence, data)
        );
    }

    private List<QuotaStreamEvent<?>> errorEvents(String sessionId,
                                                  String requestId,
                                                  AtomicInteger sequence,
                                                  Throwable error,
                                                  boolean customModel,
                                                  RunState runState) {
        long durationMillis = System.currentTimeMillis() - runState.startedAt;
        String message = errorMessage(error);
        String code = error instanceof AppException appException ? appException.getCode() : "AGENT_0001";
        agentExecutionLedgerService.recordLlmInvocation(runState.ledgerContext, runState.modelName,
                runState.question, runState.answer.toString(), AgentRun.STATUS_FAILED,
                customModel, message, durationMillis);
        agentExecutionLedgerService.finishRun(runState.run, AgentRun.STATUS_FAILED,
                runState.answer.toString(), code, message, durationMillis);
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        AgentFlowProgressResult progress = flowProgressProjector.blockCurrent(
                runState.executionPlan, runState.currentFlowStageIndex,
                normalizeErrorMessage(message, customModel));
        runState.currentFlowStageIndex = progress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        events.add(event("diagnosis_delta", sessionId, requestId, sequence, diagnosis(runState, durationMillis)));
        events.add(event("run_error", sessionId, requestId, sequence, runDone(runState.run)));
        events.add(errorEvent(sessionId, requestId, sequence, error, customModel));
        return events;
    }

    private List<QuotaStreamEvent<?>> completionEvents(UserAccount user,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       String taskType,
                                                       long startedAt,
                                                       RunState runState) {
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        if ("ppt".equals(taskType)) {
            pptArtifact(user, sessionId).ifPresent(artifact -> {
                agentArtifactService.saveArtifactRecord(user.getUserId(), sessionId, artifact,
                        runState.run.getRunId(), "", "AGENT", taskType);
                events.add(event("artifact_delta", sessionId, requestId, sequence, artifact));
            });
        }
        if ("skills".equals(taskType) || "manual-skills".equals(taskType)) {
            for (AgentSessionDetailResponse.Artifact artifact :
                    agentArtifactService.collectAndSave(user.getUserId(), sessionId, startedAt,
                            runState.run.getRunId(), "", "AGENT", taskType)) {
                events.add(event("artifact_delta", sessionId, requestId, sequence,
                        agentArtifactService.toEventPayload(artifact)));
            }
        }
        events.addAll(artifactCompletionEvents(user, sessionId, requestId, sequence, taskType, startedAt, runState));
        AgentFlowProgressResult progress = flowProgressProjector.completeRemaining(
                runState.executionPlan, runState.currentFlowStageIndex);
        runState.currentFlowStageIndex = progress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(progress, sessionId, requestId, sequence, runState));
        QuotaAccountResponse quota = userQuotaService.queryAccountResponse(user.getUserId());
        BigDecimal consumedQuota = userQuotaService.estimatePreCheckCost(taskType);
        runState.consumedQuota = consumedQuota;
        events.add(event("quota_delta", sessionId, requestId, sequence, quota));
        events.add(event("usage_metric", sessionId, requestId, sequence, Map.of(
                "consumedQuota", consumedQuota,
                "remainingQuota", quota.getQuotaBalance(),
                "model", runState.modelName)));
        recordQuotaUsageSnapshot(runState, quota, consumedQuota, taskType);
        long durationMillis = System.currentTimeMillis() - runState.startedAt;
        agentExecutionLedgerService.recordLlmInvocation(runState.ledgerContext, runState.modelName,
                runState.question, runState.answer.toString(), AgentRun.STATUS_SUCCESS,
                false, "", durationMillis);
        agentExecutionLedgerService.finishRun(runState.run, AgentRun.STATUS_SUCCESS,
                runState.answer.toString(), "", "", durationMillis);
        events.addAll(memoryCompletionEvents(user, sessionId, requestId, sequence, runState));
        events.add(event("diagnosis_delta", sessionId, requestId, sequence, diagnosis(runState, durationMillis)));
        events.add(event("run_done", sessionId, requestId, sequence, runDone(runState.run)));
        events.add(event("done", sessionId, requestId, sequence, "done"));
        return events;
    }

    private void recordQuotaUsageSnapshot(RunState runState,
                                          QuotaAccountResponse quota,
                                          BigDecimal consumedQuota,
                                          String taskType) {
        if (runState == null || runState.ledgerContext == null) {
            return;
        }
        Map<String, Object> output = quotaUsageStructuredOutput(quota, consumedQuota, taskType, runState.modelName);
        String invocationId = agentExecutionLedgerService.recordToolStart(
                runState.ledgerContext,
                "quota-usage-" + runState.run.getRequestId(),
                AgentToolOutputNames.QUOTA_USAGE,
                "quota_snapshot",
                "{}");
        agentExecutionLedgerService.recordToolFinish(
                invocationId,
                AgentRun.STATUS_SUCCESS,
                String.valueOf(output.getOrDefault("summary", "")),
                toJson(output),
                0,
                "",
                0L);
    }

    private Map<String, Object> quotaUsageStructuredOutput(QuotaAccountResponse quota,
                                                           BigDecimal consumedQuota,
                                                           String taskType,
                                                           String modelName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskType", nullToBlank(taskType));
        metadata.put("model", nullToBlank(modelName));
        metadata.put("estimatedConsumedQuota", consumedQuota == null ? BigDecimal.ZERO : consumedQuota);
        if (quota != null) {
            metadata.put("userId", nullToBlank(quota.getUserId()));
            metadata.put("remainingQuota", quota.getQuotaBalance());
            metadata.put("usedQuota", quota.getUsedQuota());
            metadata.put("frozenQuota", quota.getFrozenQuota());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("toolName", AgentToolOutputNames.QUOTA_USAGE);
        output.put("title", "额度对账快照");
        output.put("summary", "本次 Agent 运行完成后记录额度余额和预估消耗。");
        output.put("content", "额度只能以账户流水和后端交易状态为准；该快照用于历史回放和运行对账。");
        output.put("metadata", metadata);
        return output;
    }

    private List<QuotaStreamEvent<?>> artifactCompletionEvents(UserAccount user,
                                                               String sessionId,
                                                               String requestId,
                                                               AtomicInteger sequence,
                                                               String taskType,
                                                               long startedAt,
                                                               RunState runState) {
        if (!shouldCollectArtifacts(taskType, runState)) {
            return List.of();
        }
        Map<String, AgentSessionDetailResponse.Artifact> artifacts = new LinkedHashMap<>();
        try {
            for (AgentSessionDetailResponse.Artifact artifact : agentArtifactService.collectAndSave(
                    user.getUserId(), sessionId, startedAt, runState.run.getRunId(), "", "AGENT", taskType)) {
                putArtifact(artifacts, artifact);
            }
            for (AgentSessionDetailResponse.Artifact artifact : agentArtifactService.collectFromAnswerAndSave(
                    user.getUserId(), sessionId, runState.answer.toString(),
                    runState.run.getRunId(), "", "AGENT", taskType)) {
                putArtifact(artifacts, artifact);
            }
            if (shouldSaveAnswerReport(taskType, runState)) {
                AgentSessionDetailResponse.Artifact report = agentArtifactService.saveAnswerReport(
                        user.getUserId(), sessionId, runState.run.getRunId(),
                        reportTitle(runState), runState.answer.toString());
                putArtifact(artifacts, report);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        for (AgentSessionDetailResponse.Artifact artifact : artifacts.values()) {
            events.add(event("artifact_delta", sessionId, requestId, sequence,
                    agentArtifactService.toEventPayload(artifact)));
        }
        return events;
    }

    private boolean shouldCollectArtifacts(String taskType, RunState runState) {
        String type = normalizeTaskType(taskType);
        return "deep".equals(type)
                || "file".equals(type)
                || "data".equals(type)
                || "image".equals(type)
                || "ppt".equals(type)
                || (runState != null && hasAny(nullToBlank(runState.question).toLowerCase(),
                "报告", "ppt", "演示文稿", "图片", "文件", "分析"));
    }

    private boolean shouldSaveAnswerReport(String taskType, RunState runState) {
        String type = normalizeTaskType(taskType);
        return StringUtils.hasText(runState.answer.toString())
                && ("deep".equals(type)
                || "file".equals(type)
                || "data".equals(type)
                || hasAny(nullToBlank(runState.question).toLowerCase(), "报告", "方案", "分析", "总结"));
    }

    private void putArtifact(Map<String, AgentSessionDetailResponse.Artifact> artifacts,
                             AgentSessionDetailResponse.Artifact artifact) {
        if (artifact != null && StringUtils.hasText(artifact.getArtifactId())) {
            artifacts.put(artifact.getArtifactId(), artifact);
        }
    }

    private String reportTitle(RunState runState) {
        return limit(firstText(runState.question, "Agent 任务报告").replaceAll("\\s+", " ").trim(), 36);
    }

    private List<QuotaStreamEvent<?>> memoryCompletionEvents(UserAccount user,
                                                            String sessionId,
                                                            String requestId,
                                                            AtomicInteger sequence,
                                                            RunState runState) {
        List<Map<String, Object>> saved = new ArrayList<>();
        for (MemoryCandidate candidate : memoryCandidates(runState)) {
            try {
                UserAgentMemory memory = userAgentMemoryService.saveAuto(
                        user.getUserId(), candidate.memoryType(), candidate.content());
                if (memory != null && Boolean.TRUE.equals(memory.getEnabled())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("memoryType", memory.getMemoryType());
                    item.put("enabled", Boolean.TRUE.equals(memory.getEnabled()));
                    item.put("content", memory.getContent());
                    saved.add(item);
                }
            } catch (Exception ignored) {
            }
        }
        if (saved.isEmpty()) {
            return List.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("sessionId", sessionId);
        data.put("source", "run_completion");
        data.put("memoryCount", saved.size());
        data.put("memories", saved);
        return List.of(event("memory_saved", sessionId, requestId, sequence, data));
    }

    private List<MemoryCandidate> memoryCandidates(RunState runState) {
        if (runState == null) {
            return List.of();
        }
        String question = nullToBlank(runState.question);
        String answer = nullToBlank(runState.answer.toString());
        List<MemoryCandidate> candidates = new ArrayList<>();
        String style = inferOutputStyle(question, answer);
        if (StringUtils.hasText(style)) {
            candidates.add(new MemoryCandidate("output_style", style));
        }
        String background = inferBusinessContext(question, answer);
        if (StringUtils.hasText(background)) {
            candidates.add(new MemoryCandidate("business_context", background));
        }
        String preference = inferPreference(question);
        if (StringUtils.hasText(preference)) {
            candidates.add(new MemoryCandidate("preference", preference));
        }
        return candidates;
    }

    private String inferOutputStyle(String question, String answer) {
        String text = (question + "\n" + answer).toLowerCase();
        if (hasAny(text, "秋招", "面试", "简历", "项目亮点")) {
            return "偏好秋招面试表达：先讲项目价值，再讲架构、取舍、异常处理和可观测证据。";
        }
        if (hasAny(text, "报告", "方案", "总结", "分析")) {
            return "偏好结构化报告输出：先结论，再分点说明依据、风险和下一步。";
        }
        if (hasAny(text, "ppt", "演示文稿", "幻灯片")) {
            return "偏好演示文稿输出：标题清晰、页面结构紧凑、适合技术项目汇报。";
        }
        return "";
    }

    private String inferBusinessContext(String question, String answer) {
        String text = question + "\n" + answer;
        if (hasAny(text.toLowerCase(), "agent", "智能体", "多模式", "deep", "a2a")) {
            return "业务背景：用户正在建设多模式 Agent 工作台，关注 deep 任务执行、能力调用、产物沉淀和记忆成长。";
        }
        if (hasAny(text.toLowerCase(), "拼团", "交易", "额度", "支付", "订单")) {
            return "业务背景：用户项目包含拼团式额度交易平台，关注订单、支付、额度和状态一致性。";
        }
        return "";
    }

    private String inferPreference(String question) {
        String text = nullToBlank(question);
        String lower = text.toLowerCase();
        if (hasAny(lower, "完整", "补齐", "不要最小闭环", "别每次都是最小闭环")) {
            return "偏好完整交付：实现时尽量补齐可运行、可展示、可维护的闭环，避免只交占位骨架。";
        }
        if (hasAny(lower, "简历", "含金量", "秋招")) {
            return "偏好简历含金量优先：做少而硬、能讲清架构和取舍的能力。";
        }
        return "";
    }

    private record MemoryCandidate(String memoryType, String content) {
    }

    private Map<String, Object> runStart(RunState runState) {
        AgentRun run = runState.run;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRunId());
        data.put("projectId", nullToBlank(run.getProjectId()));
        data.put("requestedTaskType", runState.requestedTaskType);
        data.put("executionAgentType", runState.executionAgentType);
        data.put("taskType", runState.executionAgentType);
        data.put("question", run.getQuestion());
        data.put("model", run.getModelName());
        data.put("status", run.getStatus());
        data.put("startedAt", run.getStartedAt());
        return data;
    }

    private Map<String, Object> capabilityPlan(RunState runState) {
        List<Map<String, Object>> capabilities = plannedCapabilities(runState);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("executionAgentType", runState.executionAgentType);
        data.put("taskType", runState.executionAgentType);
        data.put("capabilityCount", capabilities.size());
        data.put("capabilities", capabilities);
        data.put("summary", capabilities.isEmpty()
                ? "本轮按普通对话执行"
                : "已规划 " + capabilities.size() + " 个能力调用");
        return data;
    }

    private List<Map<String, Object>> plannedCapabilities(RunState runState) {
        List<Map<String, Object>> result = new ArrayList<>();
        String taskType = normalizeTaskType(runState.executionAgentType);
        String question = nullToBlank(runState.question).toLowerCase();
        if ("file".equals(taskType) || StringUtils.hasText(runState.fileId) || hasAny(question, "文件", "论文", "材料", "附件", "pdf", "excel", "csv")) {
            result.add(capability("file_understanding", "文件理解", "读取并归纳用户上传或会话中的文件材料"));
        }
        if (runState.webSearchEnabled || "search".equals(taskType) || hasAny(question, "联网", "搜索", "最新", "资料", "调研", "检索")) {
            result.add(capability("web_search", "联网搜索", "检索外部资料并整理参考来源"));
        }
        if ("ppt".equals(taskType) || hasAny(question, "ppt", "演示文稿", "幻灯片")) {
            result.add(capability("ppt_generation", "PPT 生成", "生成可下载的演示文稿"));
        }
        if ("image".equals(taskType) || hasAny(question, "图片", "海报", "配图", "插画", "生成图")) {
            result.add(capability("image_generation", "图片生成", "生成图片或视觉素材"));
        }
        if ("deep".equals(taskType) || "data".equals(taskType) || hasAny(question, "报告", "总结", "方案", "分析", "输出", "整理")) {
            result.add(capability("report_generation", "报告生成", "沉淀结构化报告和可下载文本产物"));
        }
        return result;
    }

    private Map<String, Object> capability(String name, String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("title", title);
        data.put("description", description);
        data.put("status", "planned");
        return data;
    }

    private boolean hasAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> projectContext(String userId, String projectId) {
        if (!StringUtils.hasText(projectId) || agentWorkspaceService == null) {
            return Map.of();
        }
        try {
            return agentWorkspaceService.projectContext(userId, projectId);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> plan(RunState runState) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        AgentPlan executionPlan = runState.executionPlan;
        data.put("title", executionPlan.getTitle());
        data.put("steps", executionPlan.getSteps().stream()
                .map(AgentPlanStep::getInstruction)
                .toList());
        data.put("structuredSteps", executionPlan.getSteps().stream()
                .map(this::planStep)
                .toList());
        data.put("flowStages", flowProjector.buildRemainingStages(executionPlan).stream()
                .map(this::flowStage)
                .toList());
        return data;
    }

    private Map<String, Object> replan(AgentPlan previousPlan,
                                       AgentPlan nextPlan,
                                       String reason,
                                       String runId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", nullToBlank(runId));
        data.put("reason", nullToBlank(reason));
        data.put("oldPlan", previousPlan == null ? List.of() : previousPlan.getSteps().stream()
                .map(this::planStep)
                .toList());
        data.put("newPlan", nextPlan == null ? List.of() : nextPlan.getSteps().stream()
                .map(this::planStep)
                .toList());
        return data;
    }

    private AgentPlan replannedPlan(JsonNode node, AgentPlan currentPlan) {
        JsonNode planNode = node.path("plan");
        String title = firstText(planNode, "title", "planTitle");
        if (!StringUtils.hasText(title)) {
            title = firstText(node, "title", "planTitle");
        }
        if (!StringUtils.hasText(title) && currentPlan != null) {
            title = currentPlan.getTitle();
        }
        List<AgentPlanStep> steps = planSteps(firstPresentNode(
                planNode.path("structuredSteps"),
                planNode.path("steps"),
                node.path("structuredSteps"),
                node.path("remainingSteps"),
                node.path("steps")));
        if (steps.isEmpty() && currentPlan != null) {
            return currentPlan.copy();
        }
        return new AgentPlan(title, steps);
    }

    private List<AgentPlanStep> planSteps(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<AgentPlanStep> steps = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            index++;
            String instruction = item.isTextual()
                    ? item.asText()
                    : firstText(item, "instruction", "task", "title", "content", "description");
            if (!StringUtils.hasText(instruction)) {
                continue;
            }
            String stepId = item.isObject() ? firstText(item, "stepId", "id") : "";
            if (!StringUtils.hasText(stepId)) {
                stepId = "S" + (steps.size() + 1);
            }
            int order = item.isObject() ? Math.max(1, integer(item, "order", index)) : index;
            AgentPlanStep.Builder builder = AgentPlanStep.builder(stepId, instruction)
                    .order(order)
                    .assignedAgent(item.isObject() ? firstText(item, "assignedAgent", "agent") : "")
                    .dependencies(stringList(item.path("dependencies")));
            String status = item.isObject() ? firstText(item, "status") : "";
            if (StringUtils.hasText(status)) {
                builder.status(status);
            }
            steps.add(builder.build());
        }
        return steps;
    }

    private JsonNode firstPresentNode(JsonNode... nodes) {
        if (nodes == null) {
            return objectMapper.createArrayNode();
        }
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                if (node.isArray() && !node.isEmpty()) {
                    return node;
                }
                if (!node.isArray()) {
                    return node;
                }
            }
        }
        return objectMapper.createArrayNode();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private Map<String, Object> planStep(AgentPlanStep step) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stepId", step.getStepId());
        data.put("instruction", step.getInstruction());
        data.put("order", step.getOrder());
        data.put("status", step.getStatus());
        data.put("assignedAgent", step.getAssignedAgent());
        data.put("dependencies", step.getDependencies());
        return data;
    }

    private Map<String, Object> flowStage(AgentFlowStage stage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stageIndex", stage.getStageIndex());
        data.put("stepIds", stage.stepIds());
        data.put("steps", stage.getSteps().stream()
                .map(this::planStep)
                .toList());
        return data;
    }

    private List<QuotaStreamEvent<?>> flowProgressEvents(AgentFlowProgressResult progress,
                                                         String sessionId,
                                                         String requestId,
                                                         AtomicInteger sequence,
                                                         RunState runState) {
        if (progress == null || progress.getEvents().isEmpty()) {
            return List.of();
        }
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        for (AgentFlowProgress item : progress.getEvents()) {
            events.add(event("flow_delta", sessionId, requestId, sequence, flowProgress(runState, item)));
        }
        return events;
    }

    private Map<String, Object> flowProgress(RunState runState, AgentFlowProgress progress) {
        Map<String, Object> data = flowStage(progress.getStage());
        data.put("runId", runState.run.getRunId());
        data.put("status", progress.getStatus());
        data.put("message", progress.getMessage());
        return data;
    }

    private Map<String, Object> toolCall(String runId,
                                         String invocationId,
                                         String toolCallId,
                                         String toolName,
                                         String action,
                                         String argumentsJson) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", nullToBlank(runId));
        data.put("invocationId", nullToBlank(invocationId));
        data.put("toolCallId", nullToBlank(toolCallId));
        data.put("toolName", nullToBlank(toolName));
        data.put("action", nullToBlank(action));
        data.put("argumentsJson", nullToBlank(argumentsJson));
        data.put("status", AgentRun.STATUS_RUNNING);
        return data;
    }

    private Map<String, Object> toolResult(String invocationId,
                                           String toolCallId,
                                           String toolName,
                                           String status,
                                           String resultText,
                                           Map<String, Object> structuredOutput,
                                           String errorMessage,
                                           long latencyMillis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", nullToBlank(invocationId));
        data.put("toolCallId", nullToBlank(toolCallId));
        data.put("toolName", nullToBlank(toolName));
        data.put("status", nullToBlank(status));
        data.put("resultSummary", limit(resultText, 1024));
        data.put("resultJson", nullToBlank(resultText));
        data.put("errorMessage", nullToBlank(errorMessage));
        data.put("latencyMillis", Math.max(0L, latencyMillis));
        if (structuredOutput != null && !structuredOutput.isEmpty()) {
            data.put("structuredOutput", structuredOutput);
            putIfPresent(data, "fileRefs", structuredOutput.get("fileRefs"));
            putIfPresent(data, "artifactRefs", structuredOutput.get("artifactRefs"));
        }
        return data;
    }

    private Map<String, Object> runDone(AgentRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRunId());
        data.put("status", run.getStatus());
        data.put("summary", limit(run.getFinalSummary(), 1024));
        data.put("errorCode", nullToBlank(run.getErrorCode()));
        data.put("errorMessage", nullToBlank(run.getErrorMessage()));
        data.put("durationMillis", run.getDurationMillis() == null ? 0L : run.getDurationMillis());
        data.put("finishedAt", run.getFinishedAt());
        return data;
    }

    private Map<String, Object> diagnosis(RunState runState, long durationMillis) {
        double quotaConsumed = runState.consumedQuota == null ? 0.0d : runState.consumedQuota.doubleValue();
        AgentDiagnosisService.DiagnosisReport report = diagnosisService.diagnose(
                new AgentDiagnosisService.AgentRunContext(
                        runState.run.getRunId(),
                        Math.max(0L, durationMillis),
                        runState.failedToolCount,
                        quotaConsumed,
                        runState.replanCount,
                        AgentRun.STATUS_FAILED.equals(runState.run.getStatus()),
                        runState.run.getErrorMessage()));
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("elapsedMs", Math.max(0L, durationMillis));
        metrics.put("toolCallCount", runState.toolCallCount);
        metrics.put("failedToolCount", runState.failedToolCount);
        metrics.put("quotaConsumed", quotaConsumed);
        metrics.put("replanCount", runState.replanCount);
        metrics.put("capabilityCallCount", runState.capabilityCallCount);
        metrics.put("capabilityCount", runState.capabilityCount);
        metrics.put("skillCount", runState.skillCount);
        metrics.put("skillLoaded", runState.skillLoaded);
        metrics.put("memoryLoaded", runState.memoryLoaded);
        metrics.put("shortTermMemoryCount", runState.shortTermMemoryCount);
        metrics.put("taskMemoryCount", runState.taskMemoryCount);
        metrics.put("longTermMemoryCount", runState.longTermMemoryCount);
        metrics.put("calledCapabilities", runState.calledCapabilities);
        metrics.put("toolSuccessRate", runState.toolCallCount == 0
                ? 1.0d
                : (double) (runState.toolCallCount - runState.failedToolCount) / runState.toolCallCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("sessionId", runState.run.getSessionId());
        data.put("level", report.getLevel().name());
        data.put("summary", report.getSummary());
        data.put("issues", report.getIssues().stream()
                .map(item -> Map.of(
                        "level", item.getLevel().name(),
                        "code", item.getCode(),
                        "message", item.getMessage()))
                .toList());
        data.put("metrics", metrics);
        return data;
    }

    private java.util.Optional<Map<String, Object>> pptArtifact(UserAccount user, String sessionId) {
        AiPptInst inst = aiPptInstService.getLatestInst(internalSessionId(user.getUserId(), sessionId));
        if (inst == null || !StringUtils.hasText(inst.getFileUrl())) {
            return java.util.Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactId", String.valueOf(inst.getId()));
        data.put("artifactType", "PPTX");
        data.put("title", "生成的演示文稿");
        data.put("content", inst.getFileUrl());
        data.put("downloadUrl", inst.getFileUrl());
        return java.util.Optional.of(data);
    }

    private List<QuotaStreamEvent<?>> referenceEvents(JsonNode node,
                                                      String sessionId,
                                                      String requestId,
                                                      AtomicInteger sequence) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return List.of();
        }
        List<QuotaStreamEvent<?>> events = new ArrayList<>();
        if (content.isTextual()) {
            try {
                content = objectMapper.readTree(content.asText());
            } catch (Exception ignored) {
                events.add(event("reference_delta", sessionId, requestId, sequence,
                        Map.of("title", "参考资料", "content", content.asText())));
                return events;
            }
        }
        if (content.isArray()) {
            for (JsonNode item : content) {
                events.add(event("reference_delta", sessionId, requestId, sequence, reference(item)));
            }
            return events;
        }
        events.add(event("reference_delta", sessionId, requestId, sequence, reference(content)));
        return events;
    }

    private Map<String, Object> reference(JsonNode node) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", firstText(node, "title", "name", "source"));
        data.put("url", firstText(node, "url", "link"));
        data.put("content", firstText(node, "content", "snippet", "summary", "text"));
        return data;
    }

    private Map<String, Object> recommend(JsonNode node) {
        Map<String, Object> data = new LinkedHashMap<>();
        JsonNode content = node.get("content");
        data.put("items", content == null || content.isNull() ? List.of() : content);
        return data;
    }

    private Map<String, Object> checkpoint(JsonNode node) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("continueTraceId", text(node, "continueTraceId"));
        data.put("round", integer(node, "round", 0));
        return data;
    }

    private QuotaStreamEvent<?> errorEvent(String sessionId,
                                           String requestId,
                                           AtomicInteger sequence,
                                           Throwable error) {
        return errorEvent(sessionId, requestId, sequence, error, false);
    }

    private QuotaStreamEvent<?> errorEvent(String sessionId,
                                           String requestId,
                                           AtomicInteger sequence,
                                           Throwable error,
                                           boolean customModel) {
        return event("error", sessionId, requestId, sequence,
                error(error instanceof AppException appException ? appException.getCode() : "AGENT_0001",
                        errorMessage(error),
                        customModel));
    }

    private QuotaStreamEvent<?> event(String event,
                                      String sessionId,
                                      String requestId,
                                      AtomicInteger sequence,
                                      Object data) {
        return QuotaStreamEvent.of(event, sessionId, requestId, sequence.getAndIncrement(), data);
    }

    private Map<String, String> status(String stage, String message) {
        return Map.of("stage", nullToBlank(stage), "message", nullToBlank(message));
    }

    private Map<String, String> error(String code, String message) {
        return error(code, message, false);
    }

    private Map<String, String> error(String code, String message, boolean customModel) {
        return Map.of("code", StringUtils.hasText(code) ? code : "AGENT_0001",
                "message", normalizeErrorMessage(message, customModel));
    }

    private String normalizeErrorMessage(String message) {
        return normalizeErrorMessage(message, false);
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        if (error instanceof WebClientResponseException webClientException) {
            String body = webClientException.getResponseBodyAsString();
            if (StringUtils.hasText(body)) {
                return webClientException.getMessage() + " | " + body;
            }
        }
        return nullToBlank(error.getMessage());
    }

    private String normalizeErrorMessage(String message, boolean customModel) {
        if (!StringUtils.hasText(message)) {
            return "处理失败";
        }
        String lower = message.toLowerCase();
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "本次请求已处理，请勿重复提交或刷新后重试";
        }
        if (isContentInspectionMessage(lower)) {
            return "本次请求被模型服务内容安全检查拦截。可以删减敏感表达、开启新对话减少历史上下文，或关闭联网搜索后重试。";
        }
        if ((lower.contains("401 unauthorized") || lower.contains("unauthorized"))
                && (lower.contains("dashscope")
                || lower.contains("chat/completions")
                || lower.contains("openai")
                || lower.contains("api key"))) {
            if (customModel || !lower.contains("dashscope")) {
                return "自定义模型接口认证失败，请检查模型配置里的 API 地址、密钥和模型。";
            }
            return "模型密钥无效或权限不足，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥。";
        }
        if (lower.contains("api key") && (lower.contains("invalid") || lower.contains("not configured"))) {
            if (customModel || !lower.contains("dashscope")) {
                return "自定义模型配置不可用，请检查模型配置里的 API 地址、密钥和模型。";
            }
            return "模型密钥未配置或不可用，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥。";
        }
        return message;
    }

    private boolean hasCustomModelConfig(AgentStreamRequest request) {
        return request != null
                && (StringUtils.hasText(request.getLlmBaseUrl())
                || StringUtils.hasText(request.getLlmApiKey())
                || StringUtils.hasText(request.getLlmModel()));
    }

    private AgentSessionSummaryDTO toSummary(UserAccount user, AiSession session) {
        AgentSessionSummaryDTO dto = new AgentSessionSummaryDTO();
        dto.setSessionId(agentNativeService.externalConversationId(user.getUserId(), session.getSessionId()));
        dto.setTaskType(normalizeTaskType(session.getAgentType()));
        dto.setTitle(title(session.getQuestion(), session.getAgentType()));
        dto.setLastMessage(limit(session.getAnswer(), 120));
        dto.setUpdateTime(session.getUpdateTime() == null ? session.getCreateTime() : session.getUpdateTime());
        return dto;
    }

    private AgentRunDetailResponse.Run toRun(AgentRun run) {
        AgentRunDetailResponse.Run dto = new AgentRunDetailResponse.Run();
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

    private AgentSessionDetailResponse.Message toMessage(String role, String content, LocalDateTime createTime) {
        return toMessage("", role, content, createTime);
    }

    private AgentSessionDetailResponse.Message toMessage(String messageId, String role, String content, LocalDateTime createTime) {
        AgentSessionDetailResponse.Message message = new AgentSessionDetailResponse.Message();
        message.setMessageId(nullToBlank(messageId));
        message.setRole(role);
        message.setContent(nullToBlank(content));
        message.setImageUrl("");
        message.setCreateTime(createTime);
        return message;
    }

    private void appendFailureAssistantIfNeeded(String userId,
                                                String sessionId,
                                                List<AgentSessionDetailResponse.Message> messages) {
        if (messages.isEmpty() || !"USER".equals(messages.get(messages.size() - 1).getRole())) {
            return;
        }
        try {
            List<AgentRun> runs = agentExecutionLedgerService.queryRuns(userId, sessionId, 1);
            if (runs.isEmpty()) {
                return;
            }
            AgentRun latestRun = runs.get(0);
            if (!AgentRun.STATUS_FAILED.equals(latestRun.getStatus())) {
                return;
            }
            LocalDateTime createTime = latestRun.getFinishedAt() == null ? LocalDateTime.now() : latestRun.getFinishedAt();
            messages.add(toMessage("ASSISTANT", failureAnswer(latestRun), createTime));
        } catch (Exception ignored) {
        }
    }

    private String failureAnswer(AgentRun run) {
        String partialAnswer = agentArtifactService.sanitizeLocalPaths(nullToBlank(run.getFinalSummary()).trim());
        String errorMessage = normalizeErrorMessage(run.getErrorMessage());
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(partialAnswer)) {
            builder.append("已生成的部分内容：\n")
                    .append(partialAnswer)
                    .append("\n\n");
        }
        if (isContentInspectionMessage(run.getErrorMessage())) {
            builder.append("本次生成失败，模型服务内容安全检查拦截了本次请求。");
        } else {
            builder.append("本次生成失败，模型服务返回错误，请检查模型配置或稍后重试。");
        }
        if (StringUtils.hasText(errorMessage)) {
            builder.append("\n\n错误信息：").append(limit(errorMessage, 500));
        }
        return builder.toString();
    }

    private boolean isContentInspectionMessage(String message) {
        String lower = nullToBlank(message).toLowerCase();
        return lower.contains("data_inspection_failed")
                || lower.contains("inappropriate content")
                || lower.contains("content security")
                || lower.contains("input data may contain inappropriate content");
    }

    private Object parseRecommend(String recommendJson) {
        if (!StringUtils.hasText(recommendJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(recommendJson, Object.class);
        } catch (Exception ignored) {
            return List.of(recommendJson);
        }
    }

    private List<AgentSessionDetailResponse.Reference> parseReferences(String referenceJson) {
        if (!StringUtils.hasText(referenceJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(referenceJson);
            JsonNode content = root.path("content");
            if (content.isTextual()) {
                content = objectMapper.readTree(content.asText());
            }
            if (!content.isArray()) {
                return List.of();
            }
            List<AgentSessionDetailResponse.Reference> references = new ArrayList<>();
            for (JsonNode item : content) {
                AgentSessionDetailResponse.Reference reference = new AgentSessionDetailResponse.Reference();
                reference.setTitle(firstText(item, "title", "name", "source"));
                reference.setUrl(firstText(item, "url", "link"));
                reference.setContent(firstText(item, "content", "snippet", "summary", "text"));
                references.add(reference);
            }
            return references;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeTaskType(String taskType) {
        String type = StringUtils.hasText(taskType) ? taskType.trim().toLowerCase() : "chat";
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
            case "auto", "smart", "orchestrator" -> UnifiedAgentOrchestrator.AUTO_TASK_TYPE;
            default -> "chat";
        };
    }

    private String toFrontendTaskType(String agentType) {
        return switch (normalizeTaskType(agentType)) {
            case "file" -> "paper";
            case "deep" -> "deep-research";
            default -> normalizeTaskType(agentType);
        };
    }

    private String executionMemoryPrompt(String userId, String sessionId, String currentRequestId) {
        try {
            return buildExecutionMemoryPrompt(agentExecutionLedgerService.querySessionMemory(
                    userId, sessionId, currentRequestId, 6));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String outputStylePrompt(String outputStyle) {
        return switch (normalizeOutputStyle(outputStyle)) {
            case "brief" -> """
                    ## Output style
                    Keep the final answer concise. Prioritize direct conclusions, key evidence and next actions.
                    """;
            case "report" -> """
                    ## Output style
                    Produce a structured report. Use clear sections, evidence, assumptions, risks and actionable conclusions.
                    """;
            case "interview" -> """
                    ## Output style
                    Explain the answer as an interview project highlight. Emphasize architecture, trade-offs, failure handling, observability and business value.
                    """;
            case "html" -> """
                    ## Output style
                    When a deliverable is needed, prefer an HTML-style report structure with title, summary, sections, tables and a final conclusion.
                    """;
            default -> "";
        };
    }

    private String effectiveOutputStyle(String taskType, String outputStyle) {
        if (StringUtils.hasText(outputStyle)) {
            return outputStyle;
        }
        return "";
    }

    private String normalizeOutputStyle(String outputStyle) {
        String style = nullToBlank(outputStyle).trim().toLowerCase();
        return switch (style) {
            case "brief", "report", "interview", "html" -> style;
            default -> "";
        };
    }

    private String joinPrompts(String... prompts) {
        if (prompts == null || prompts.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String prompt : prompts) {
            if (!StringUtils.hasText(prompt)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(prompt.trim());
        }
        return builder.toString();
    }

    private String buildExecutionMemoryPrompt(AgentSessionDetailResponse.MemorySnapshot memory) {
        if (memory == null || memory.getRuns().isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Session execution memory\n");
        prompt.append("Use this memory only as context for the current user request. ");
        prompt.append("Do not claim an artifact is newly created unless it is produced in the current run.\n");
        if (StringUtils.hasText(memory.getSummary())) {
            prompt.append("Summary: ").append(limit(memory.getSummary(), 600)).append('\n');
        }
        if (StringUtils.hasText(memory.getHistoryDialogue())) {
            prompt.append('\n').append(limit(memory.getHistoryDialogue(), 3000)).append('\n');
        }
        if (!memory.getReusableArtifacts().isEmpty()) {
            prompt.append("\nReusable artifacts:\n");
            for (AgentSessionDetailResponse.Artifact artifact : memory.getReusableArtifacts()) {
                prompt.append("- ")
                        .append(firstText(artifact.getTitle(), artifact.getFileName(), artifact.getArtifactId()));
                if (StringUtils.hasText(artifact.getDownloadUrl())) {
                    prompt.append(" url=").append(artifact.getDownloadUrl());
                }
                prompt.append('\n');
            }
        }
        if (!memory.getToolObservations().isEmpty()) {
            prompt.append("\nRecent tool observations:\n");
            for (AgentSessionDetailResponse.ToolObservation observation : memory.getToolObservations()) {
                prompt.append("- ")
                        .append(firstText(observation.getToolName(), "tool"))
                        .append(" [").append(firstText(observation.getStatus(), "UNKNOWN")).append("]");
                if (StringUtils.hasText(observation.getResultSummary())) {
                    prompt.append(": ").append(limit(observation.getResultSummary(), 300));
                }
                prompt.append('\n');
            }
        }
        return limit(prompt.toString().trim(), 5000);
    }

    private boolean isModelIdentityQuestion(String query) {
        String normalized = nullToBlank(query).toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return (normalized.contains("什么模型")
                || normalized.contains("哪款模型")
                || normalized.contains("底层模型")
                || normalized.contains("模型版本")
                || normalized.contains("你是谁")
                || normalized.contains("who are you")
                || normalized.contains("what model")
                || normalized.contains("which model"))
                && !normalized.contains("对比")
                && !normalized.contains("列表")
                && !normalized.contains("有哪些模型");
    }

    private String identityAnswer(String modelName) {
        String safeModel = StringUtils.hasText(modelName) ? modelName.trim() : "qwen3.7-plus";
        return "我是熊博士Agent，一个应用层智能体助手，当前默认文本模型配置是 "
                + safeModel
                + "；实际底座以后台或用户模型配置为准。";
    }

    private String normalizeQuery(AgentStreamRequest request, String taskType) {
        String question = PromptInjectionGuard.sanitize(
                request == null ? "" : nullToBlank(request.getQuestion()).trim());
        if ("ppt".equals(taskType)) {
            return normalizePptQuery(question);
        }
        if ("image".equals(taskType) && !StringUtils.hasText(question)) {
            return "请生成一张适合项目展示的智能体平台概念图。";
        }
        if ("data".equals(taskType) && !StringUtils.hasText(question)) {
            return "请分析近五年 RAG 相关项目资料趋势、主要方案和指标差异。";
        }
        if ("trade-diagnosis".equals(taskType) && !StringUtils.hasText(question)) {
            return "请只读取当前用户的订单、支付、退款和额度流水状态，诊断是否存在交易一致性异常。";
        }
        if (StringUtils.hasText(question)) {
            return question;
        }
        if (StringUtils.hasText(request == null ? "" : request.getFileId())) {
            return "请分析这个文件。";
        }
        return "你好";
    }

    private String normalizePptQuery(String question) {
        String topic = StringUtils.hasText(question) ? question : "请生成一份演示文稿";
        String normalized = topic.strip();
        boolean hasPageCount = normalized.matches("(?s).*\\d+\\s*(页|p|P|slides?|Slides?).*");
        StringBuilder builder = new StringBuilder(normalized);
        builder.append("\n\n请直接生成PPT，不要再追问用户。");
        builder.append("\n默认补齐以下生成信息：");
        builder.append("\n- 页数：").append(hasPageCount ? "按用户要求" : "5页");
        builder.append("\n- 风格建议：科技感、简洁商务蓝，适合技术项目汇报");
        builder.append("\n- 受众群体：计算机硕士秋招技术岗面试官和HR");
        builder.append("\n- 输出要求：生成可下载的真实PPTX文件");
        builder.append("\n- 系统能力：后端会使用python-pptx渲染真实PPTX并上传到MinIO，请不要声称当前环境无法生成二进制PPTX文件");
        return builder.toString();
    }

    private String title(String question, String fallback) {
        if (StringUtils.hasText(question)) {
            return limit(question.replaceAll("\\s+", " ").trim(), 24);
        }
        return switch (normalizeTaskType(fallback)) {
            case "file" -> "文件问答";
            case "ppt" -> "PPT生成";
            case "deep" -> "深度任务";
            case "image" -> "图像生成";
            case "data" -> "数据问答";
            case "skills" -> "技能助手";
            case "manual-skills" -> "手动技能";
            default -> "新对话";
        };
    }

    private String modelName(String userId, AgentStreamRequest request) {
        if (request != null && StringUtils.hasText(request.getLlmModel())) {
            return request.getLlmModel().trim();
        }
        try {
            if (!userQuotaService.hasEnabledModelConfig(userId)) {
                return "qwen3.7-plus";
            }
            String storedModel = userQuotaService.queryModelConfigResponse(userId).getModel();
            if (StringUtils.hasText(storedModel)) {
                return storedModel.trim();
            }
        } catch (Exception ignored) {
        }
        return "qwen3.7-plus";
    }

    private String content(JsonNode node) {
        return jsonCodec.content(node);
    }

    private String jsonOrText(JsonNode node, String... fields) {
        return jsonCodec.jsonOrText(node, fields);
    }

    private Map<String, Object> parseObject(String json) {
        return jsonCodec.parseObject(json);
    }

    private String toJson(Map<String, Object> data) {
        return jsonCodec.toJson(data);
    }

    private void putIfPresent(Map<String, Object> data, String key, Object value) {
        jsonCodec.putIfPresent(data, key, value);
    }

    private String text(JsonNode node, String field) {
        return jsonCodec.text(node, field);
    }

    private boolean isFailureStatus(String status) {
        String text = nullToBlank(status).toLowerCase();
        return text.contains("fail") || text.contains("error");
    }

    private int integer(JsonNode node, String field, int fallback) {
        return jsonCodec.integer(node, field, fallback);
    }

    private long longValue(JsonNode node, String field, long fallback) {
        return jsonCodec.longValue(node, field, fallback);
    }

    private boolean booleanValue(JsonNode node, String field, boolean fallback) {
        return jsonCodec.booleanValue(node, field, fallback);
    }

    private String firstText(JsonNode node, String... fields) {
        return jsonCodec.firstText(node, fields);
    }

    private String firstText(String... values) {
        return jsonCodec.firstText(values);
    }

    private Map<String, Object> objectValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(Objects.toString(key, ""), item));
        return result;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(nullToBlank(Objects.toString(value, ""))));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String toolKey(String toolCallId, String toolName) {
        return StringUtils.hasText(toolCallId) ? toolCallId : nullToBlank(toolName);
    }

    private String limit(String value, int maxLength) {
        String safe = nullToBlank(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String internalSessionId(String userId, String sessionId) {
        return userId + ":" + nullToBlank(sessionId).trim();
    }

    private static class RunState {
        private final AgentRun run;
        private final AgentLedgerContext.Context ledgerContext;
        private final String question;
        private final String modelName;
        private final long startedAt;
        private final boolean webSearchEnabled;
        private final String requestedTaskType;
        private final String executionAgentType;
        private AgentPlan executionPlan;
        private final UnifiedAgentOrchestrator.OrchestrationPlan orchestrationPlan;
        private final StringBuilder answer = new StringBuilder();
        private final Map<String, String> toolInvocations = new LinkedHashMap<>();
        private int currentFlowStageIndex = -1;
        private int toolCallCount;
        private int failedToolCount;
        private int replanCount;
        private int capabilityCallCount;
        private int capabilityCount;
        private int skillCount;
        private int shortTermMemoryCount;
        private int taskMemoryCount;
        private int longTermMemoryCount;
        private boolean memoryLoaded;
        private boolean skillLoaded;
        private BigDecimal consumedQuota = BigDecimal.ZERO;
        private String projectId = "";
        private String fileId = "";
        private Map<String, Object> projectContext = Map.of();
        private final Map<String, Integer> calledCapabilities = new LinkedHashMap<>();

        private RunState(AgentRun run,
                         AgentLedgerContext.Context ledgerContext,
                         String question,
                         String modelName,
                         long startedAt,
                         boolean webSearchEnabled,
                         AgentPlan executionPlan,
                         UnifiedAgentOrchestrator.OrchestrationPlan orchestrationPlan,
                         String requestedTaskType,
                         String executionAgentType) {
            this.run = run;
            this.ledgerContext = ledgerContext;
            this.question = question;
            this.modelName = modelName;
            this.startedAt = startedAt;
            this.webSearchEnabled = webSearchEnabled;
            this.executionPlan = executionPlan;
            this.orchestrationPlan = orchestrationPlan;
            this.requestedTaskType = requestedTaskType == null ? "chat" : requestedTaskType;
            this.executionAgentType = executionAgentType == null ? "chat" : executionAgentType;
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String effectiveFileIds(AgentStreamRequest request) {
        if (request == null) {
            return "";
        }
        List<String> selectedFileIds = request.getSelectedFileIds();
        if (selectedFileIds != null && !selectedFileIds.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (String selectedFileId : selectedFileIds) {
                String fileId = nullToBlank(selectedFileId).trim();
                if (StringUtils.hasText(fileId) && !result.contains(fileId)) {
                    result.add(fileId);
                }
            }
            if (!result.isEmpty()) {
                return String.join(",", result);
            }
        }
        return nullToBlank(request.getFileId()).trim();
    }
}












