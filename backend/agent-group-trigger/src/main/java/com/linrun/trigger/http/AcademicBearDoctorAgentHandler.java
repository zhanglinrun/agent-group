package com.linrun.trigger.http;

import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.api.dto.AcademicFileUploadResponse;
import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AcademicSessionSummaryDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AcademicBearDoctorAgentHandler {

    private final BearDoctorNativeAgentService bearDoctorNativeAgentService;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AgentTaskManager taskManager;
    private final AiPptInstService aiPptInstService;
    private final AcademicBackgroundStreamService backgroundStreamService;
    private final AcademicArtifactService academicArtifactService;
    private final AcademicExecutionLedgerService academicExecutionLedgerService;
    private final ObjectMapper objectMapper;

    public AcademicBearDoctorAgentHandler(BearDoctorNativeAgentService bearDoctorNativeAgentService,
                                    UserAccountService userAccountService,
                                    UserQuotaService userQuotaService,
                                    AgentTaskManager taskManager,
                                    AiPptInstService aiPptInstService,
                                    AcademicBackgroundStreamService backgroundStreamService,
                                    AcademicArtifactService academicArtifactService,
                                    AcademicExecutionLedgerService academicExecutionLedgerService,
                                    ObjectMapper objectMapper) {
        this.bearDoctorNativeAgentService = bearDoctorNativeAgentService;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.taskManager = taskManager;
        this.aiPptInstService = aiPptInstService;
        this.backgroundStreamService = backgroundStreamService;
        this.academicArtifactService = academicArtifactService;
        this.academicExecutionLedgerService = academicExecutionLedgerService;
        this.objectMapper = objectMapper;
    }

    public Flux<GuideStreamEvent<?>> backgroundStreamEventFlux(String token,
                                                               AcademicAgentStreamRequest request,
                                                               String sessionId,
                                                               String requestId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String taskKey = internalSessionId(user.getUserId(), sessionId);
        return backgroundStreamService.startOrAttach(taskKey,
                () -> streamEventFlux(token, request, sessionId, requestId));
    }

    public Flux<GuideStreamEvent<?>> attachEventFlux(String token,
                                                     String sessionId,
                                                     String requestId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String taskKey = internalSessionId(user.getUserId(), sessionId);
        return backgroundStreamService.attach(taskKey)
                .switchIfEmpty(Flux.just(event("done", sessionId, requestId, new AtomicInteger(1), "done")));
    }

    public Flux<GuideStreamEvent<?>> streamEventFlux(String token,
                                                     AcademicAgentStreamRequest request,
                                                     String sessionId,
                                                     String requestId) {
        return Flux.defer(() -> {
            UserAccount user = userAccountService.requireUserByToken(token);
            AcademicAgentStreamRequest safeRequest = request == null ? new AcademicAgentStreamRequest() : request;
            String taskType = normalizeTaskType(safeRequest.getTaskType());
            String query = normalizeQuery(safeRequest, taskType);
            String fileId = nullToBlank(safeRequest.getFileId());
            boolean webSearchEnabled = Boolean.TRUE.equals(safeRequest.getWebSearchEnabled());
            long startedAt = System.currentTimeMillis();
            AtomicInteger sequence = new AtomicInteger(1);
            String modelName = modelName(safeRequest);
            AcademicAgentRun run = academicExecutionLedgerService.startRun(
                    user.getUserId(), sessionId, requestId, taskType, query, modelName);
            AcademicLedgerContext.Context ledgerContext = new AcademicLedgerContext.Context(
                    run.getRunId(), requestId, sessionId, user.getUserId(), taskType);
            RunState runState = new RunState(run, ledgerContext, query, modelName, startedAt, webSearchEnabled);

            return Flux.concat(
                            Flux.fromIterable(startEvents(runState, sessionId, requestId, sequence)),
                            Flux.defer(() -> bearDoctorNativeAgentService.stream(token, taskType, query, sessionId, fileId,
                                    webSearchEnabled, safeRequest.getLlmBaseUrl(), safeRequest.getLlmApiKey(), safeRequest.getLlmModel()))
                                    .doOnSubscribe(subscription -> AcademicLedgerContext.set(ledgerContext))
                                    .flatMapIterable(raw -> toEvents(raw, sessionId, requestId, sequence, runState))
                                    .concatWith(Flux.defer(() -> Flux.fromIterable(completionEvents(
                                            user, sessionId, requestId, sequence, taskType, startedAt, runState)))))
                    .onErrorResume(error -> Flux.fromIterable(errorEvents(
                            sessionId, requestId, sequence, error, hasCustomModelConfig(safeRequest), runState)))
                    .doFinally(signalType -> AcademicLedgerContext.clear());
        });
    }

    public AcademicAgentStreamRequest resumeRequest(String token, String sessionId) {
        List<AiSession> messages = bearDoctorNativeAgentService.querySessionMessages(token, sessionId);
        AiSession latest = messages.stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AppException("SESSION_0001", "会话不存在，无法继续生成"));
        AcademicAgentStreamRequest request = new AcademicAgentStreamRequest();
        request.setSessionId(sessionId);
        request.setTaskType(toFrontendTaskType(latest.getAgentType()));
        request.setFileId(nullToBlank(latest.getFileid()));
        request.setQuestion("请从上次停止处继续完成这个任务，避免重复已经完成的内容。");
        return request;
    }

    public Map<String, Object> queryTaskStatus(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        String internalSessionId = internalSessionId(user.getUserId(), sessionId);
        List<AiSession> messages = bearDoctorNativeAgentService.querySessionMessages(token, sessionId);
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

    public AcademicFileUploadResponse upload(String token, MultipartFile file, String sessionId) {
        FileInfo fileInfo = bearDoctorNativeAgentService.upload(token, file, sessionId);
        AcademicFileUploadResponse response = new AcademicFileUploadResponse();
        response.setFileId(fileInfo.getFileId());
        response.setFileName(fileInfo.getFileName());
        response.setFileType(fileInfo.getFileType());
        response.setFileSize(fileInfo.getFileSize());
        response.setSummary(limit(fileInfo.getExtractedText(), 500));
        response.setStatus(fileInfo.getStatus() == null ? "" : fileInfo.getStatus().name());
        return response;
    }

    public boolean stop(String token, String sessionId) {
        return bearDoctorNativeAgentService.stop(token, sessionId);
    }

    public void deleteSession(String token, String sessionId) {
        bearDoctorNativeAgentService.deleteSession(token, sessionId);
    }

    public List<AcademicSessionSummaryDTO> querySessions(String token, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return bearDoctorNativeAgentService.querySessions(token, 1, Math.max(1, Math.min(limit, 100)))
                .stream()
                .map(session -> toSummary(user, session))
                .toList();
    }

    public AcademicSessionDetailResponse queryDetail(String token, String sessionId) {
        AcademicSessionDetailResponse response = new AcademicSessionDetailResponse();
        response.setSessionId(sessionId);
        List<AcademicSessionDetailResponse.Message> messages = new ArrayList<>();
        String lastAssistantAnswer = "";
        for (AiSession session : bearDoctorNativeAgentService.querySessionMessages(token, sessionId)) {
            if (StringUtils.hasText(session.getQuestion())) {
                messages.add(toMessage("USER", session.getQuestion(), session.getCreateTime()));
            }
            if (StringUtils.hasText(session.getAnswer())) {
                lastAssistantAnswer = session.getAnswer();
                AcademicSessionDetailResponse.Message assistantMessage =
                        toMessage("ASSISTANT", academicArtifactService.sanitizeLocalPaths(session.getAnswer()), session.getUpdateTime());
                assistantMessage.setReferences(parseReferences(session.getReference()));
                assistantMessage.setRecommend(parseRecommend(session.getRecommend()));
                messages.add(assistantMessage);
            }
        }
        UserAccount user = userAccountService.requireUserByToken(token);
        List<AcademicSessionDetailResponse.Artifact> artifacts =
                academicArtifactService.loadManifest(user.getUserId(), sessionId);
        if (artifacts.isEmpty()) {
            artifacts = academicArtifactService.collectFromAnswerAndSave(user.getUserId(), sessionId, lastAssistantAnswer);
        }
        if (!artifacts.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                AcademicSessionDetailResponse.Message message = messages.get(i);
                if ("ASSISTANT".equals(message.getRole())) {
                    message.setArtifacts(artifacts);
                    break;
                }
            }
        }
        appendFailureAssistantIfNeeded(user.getUserId(), sessionId, messages);
        try {
            response.setReplays(academicExecutionLedgerService.querySessionReplays(user.getUserId(), sessionId));
        } catch (Exception ignored) {
            response.setReplays(List.of());
        }
        response.setMessages(messages);
        return response;
    }

    public List<AcademicRunDetailResponse.Run> queryRuns(String token, String sessionId, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return academicExecutionLedgerService.queryRuns(user.getUserId(), sessionId, limit).stream()
                .map(this::toRun)
                .toList();
    }

    public AcademicRunDetailResponse queryRunDetail(String token, String runId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return academicExecutionLedgerService.queryRunDetail(user.getUserId(), runId);
    }

    public List<AcademicReplayResponse> queryReplay(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return academicExecutionLedgerService.querySessionReplays(user.getUserId(), sessionId);
    }

    public AcademicReplayResponse queryRunReplay(String token, String runId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return academicExecutionLedgerService.queryRunReplay(user.getUserId(), runId);
    }

    public AcademicArtifactService.DownloadArtifact downloadArtifact(String token,
                                                                     String sessionId,
                                                                     String artifactId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        List<AiSession> messages = bearDoctorNativeAgentService.querySessionMessages(token, sessionId);
        if (messages.isEmpty()) {
            throw new AppException("ARTIFACT_0004", "会话不存在或无权访问");
        }
        List<AcademicSessionDetailResponse.Artifact> artifacts =
                academicArtifactService.loadManifest(user.getUserId(), sessionId);
        boolean allowed = artifacts.stream().anyMatch(artifact -> artifactId.equals(artifact.getArtifactId()));
        if (!allowed) {
            throw new AppException("ARTIFACT_0004", "会话不存在或无权访问");
        }
        return academicArtifactService.resolveDownload(artifactId);
    }

    private List<GuideStreamEvent<?>> toEvents(String raw,
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
                case "tool_start" -> toolStartEvents(node, sessionId, requestId, sequence, runState);
                case "tool_end" -> toolEndEvents(node, sessionId, requestId, sequence, runState);
                case "reference" -> referenceEvents(node, sessionId, requestId, sequence);
                case "recommend" -> List.of(event("recommend_delta", sessionId, requestId, sequence, recommend(node)));
                case "error" -> List.of(event("error", sessionId, requestId, sequence,
                        error(text(node, "code"), firstText(node, "message", "content", "detail"))));
                case "complete" -> List.of();
                default -> rawAnswerEvent(raw, sessionId, requestId, sequence, runState);
            };
        } catch (Exception e) {
            return rawAnswerEvent(raw, sessionId, requestId, sequence, runState);
        }
    }

    private List<GuideStreamEvent<?>> startEvents(RunState runState,
                                                  String sessionId,
                                                  String requestId,
                                                  AtomicInteger sequence) {
        return List.of(
                event("run_start", sessionId, requestId, sequence, runStart(runState.run)),
                event("plan_delta", sessionId, requestId, sequence, plan(runState))
        );
    }

    private List<GuideStreamEvent<?>> answerEvents(JsonNode node,
                                                   String sessionId,
                                                   String requestId,
                                                   AtomicInteger sequence,
                                                   RunState runState) {
        String content = academicArtifactService.sanitizeLocalPaths(content(node));
        runState.answer.append(content);
        return List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", content)));
    }

    private List<GuideStreamEvent<?>> rawAnswerEvent(String raw,
                                                     String sessionId,
                                                     String requestId,
                                                     AtomicInteger sequence,
                                                     RunState runState) {
        String content = academicArtifactService.sanitizeLocalPaths(raw);
        runState.answer.append(content);
        return List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", content)));
    }

    private List<GuideStreamEvent<?>> toolStartEvents(JsonNode node,
                                                      String sessionId,
                                                      String requestId,
                                                      AtomicInteger sequence,
                                                      RunState runState) {
        String toolName = firstText(node, "toolName", "name", "tool");
        String toolCallId = firstText(node, "toolCallId", "tool_call_id", "id");
        String action = firstText(node, "action", "stage");
        String argumentsJson = jsonOrText(node, "arguments", "args", "input", "content");
        String invocationId = academicExecutionLedgerService.recordToolStart(
                runState.ledgerContext, toolCallId, toolName, action, argumentsJson);
        runState.toolInvocations.put(toolKey(toolCallId, toolName), invocationId);
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        events.add(event("task_status", sessionId, requestId, sequence,
                status("TOOL", "开始调用工具：" + nullToBlank(toolName))));
        events.add(event("tool_call", sessionId, requestId, sequence,
                toolCall(runState.run.getRunId(), invocationId, toolCallId, toolName, action, argumentsJson)));
        return events;
    }

    private List<GuideStreamEvent<?>> toolEndEvents(JsonNode node,
                                                    String sessionId,
                                                    String requestId,
                                                    AtomicInteger sequence,
                                                    RunState runState) {
        String toolName = firstText(node, "toolName", "name", "tool");
        String toolCallId = firstText(node, "toolCallId", "tool_call_id", "id");
        String invocationId = runState.toolInvocations.getOrDefault(toolKey(toolCallId, toolName), "");
        String resultText = jsonOrText(node, "result", "output", "content", "detail");
        String rawStatus = firstText(node, "status", "state");
        String status = isFailureStatus(rawStatus) ? AcademicAgentRun.STATUS_FAILED : AcademicAgentRun.STATUS_SUCCESS;
        String errorMessage = AcademicAgentRun.STATUS_FAILED.equals(status)
                ? firstText(node, "message", "error", "detail")
                : "";
        long latencyMillis = longValue(node, "latencyMillis", 0L);
        academicExecutionLedgerService.recordToolFinish(invocationId, status,
                limit(resultText, 1024), resultText, integer(node, "retryCount", 0), errorMessage, latencyMillis);
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        events.add(event("task_status", sessionId, requestId, sequence,
                status("TOOL", "工具调用完成：" + nullToBlank(toolName))));
        events.add(event("tool_result", sessionId, requestId, sequence,
                toolResult(invocationId, toolName, status, resultText, errorMessage, latencyMillis)));
        return events;
    }

    private List<GuideStreamEvent<?>> errorEvents(String sessionId,
                                                  String requestId,
                                                  AtomicInteger sequence,
                                                  Throwable error,
                                                  boolean customModel,
                                                  RunState runState) {
        long durationMillis = System.currentTimeMillis() - runState.startedAt;
        String message = errorMessage(error);
        String code = error instanceof AppException appException ? appException.getCode() : "AGENT_0001";
        academicExecutionLedgerService.recordLlmInvocation(runState.ledgerContext, runState.modelName,
                runState.question, runState.answer.toString(), AcademicAgentRun.STATUS_FAILED,
                customModel, message, durationMillis);
        academicExecutionLedgerService.finishRun(runState.run, AcademicAgentRun.STATUS_FAILED,
                runState.answer.toString(), code, message, durationMillis);
        return List.of(
                event("run_error", sessionId, requestId, sequence, runDone(runState.run)),
                errorEvent(sessionId, requestId, sequence, error, customModel)
        );
    }

    private List<GuideStreamEvent<?>> completionEvents(UserAccount user,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       String taskType,
                                                       long startedAt,
                                                       RunState runState) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        if ("ppt".equals(taskType)) {
            pptArtifact(user, sessionId).ifPresent(artifact -> {
                academicArtifactService.saveArtifactRecord(user.getUserId(), sessionId, artifact,
                        runState.run.getRunId(), "", "AGENT", taskType);
                events.add(event("artifact_delta", sessionId, requestId, sequence, artifact));
            });
        }
        if ("skills".equals(taskType)) {
            for (AcademicSessionDetailResponse.Artifact artifact :
                    academicArtifactService.collectAndSave(user.getUserId(), sessionId, startedAt,
                            runState.run.getRunId(), "", "AGENT", taskType)) {
                events.add(event("artifact_delta", sessionId, requestId, sequence,
                        academicArtifactService.toEventPayload(artifact)));
            }
        }
        QuotaAccountResponse quota = userQuotaService.queryAccountResponse(user.getUserId());
        events.add(event("quota_delta", sessionId, requestId, sequence, quota));
        events.add(event("usage_metric", sessionId, requestId, sequence, Map.of(
                "consumedQuota", userQuotaService.estimatePreCheckCost(taskType),
                "remainingQuota", quota.getQuotaBalance(),
                "model", runState.modelName)));
        long durationMillis = System.currentTimeMillis() - runState.startedAt;
        academicExecutionLedgerService.recordLlmInvocation(runState.ledgerContext, runState.modelName,
                runState.question, runState.answer.toString(), AcademicAgentRun.STATUS_SUCCESS,
                false, "", durationMillis);
        academicExecutionLedgerService.finishRun(runState.run, AcademicAgentRun.STATUS_SUCCESS,
                runState.answer.toString(), "", "", durationMillis);
        events.add(event("run_done", sessionId, requestId, sequence, runDone(runState.run)));
        events.add(event("done", sessionId, requestId, sequence, "done"));
        return events;
    }

    private Map<String, Object> runStart(AcademicAgentRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRunId());
        data.put("taskType", run.getTaskType());
        data.put("question", run.getQuestion());
        data.put("model", run.getModelName());
        data.put("status", run.getStatus());
        data.put("startedAt", run.getStartedAt());
        return data;
    }

    private Map<String, Object> plan(RunState runState) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runState.run.getRunId());
        data.put("steps", planSteps(runState.run.getTaskType(), runState.webSearchEnabled));
        return data;
    }

    private List<String> planSteps(String taskType, boolean webSearchEnabled) {
        return switch (normalizeTaskType(taskType)) {
            case "file" -> List.of("读取文件", "检索相关内容", "生成回答");
            case "ppt" -> List.of("拆解主题", webSearchEnabled ? "搜索资料" : "整理素材", "生成演示文稿");
            case "deep" -> List.of("拆解问题", webSearchEnabled ? "搜索资料" : "梳理已有信息", "汇总结论");
            case "skills" -> List.of("选择技能", "执行工具", "整理产物");
            default -> List.of("理解问题", webSearchEnabled ? "检索或搜索" : "组织回答", "生成回答");
        };
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
        data.put("status", AcademicAgentRun.STATUS_RUNNING);
        return data;
    }

    private Map<String, Object> toolResult(String invocationId,
                                           String toolName,
                                           String status,
                                           String resultText,
                                           String errorMessage,
                                           long latencyMillis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", nullToBlank(invocationId));
        data.put("toolName", nullToBlank(toolName));
        data.put("status", nullToBlank(status));
        data.put("resultSummary", limit(resultText, 1024));
        data.put("resultJson", nullToBlank(resultText));
        data.put("errorMessage", nullToBlank(errorMessage));
        data.put("latencyMillis", Math.max(0L, latencyMillis));
        return data;
    }

    private Map<String, Object> runDone(AcademicAgentRun run) {
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

    private List<GuideStreamEvent<?>> referenceEvents(JsonNode node,
                                                      String sessionId,
                                                      String requestId,
                                                      AtomicInteger sequence) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return List.of();
        }
        List<GuideStreamEvent<?>> events = new ArrayList<>();
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

    private GuideStreamEvent<?> errorEvent(String sessionId,
                                           String requestId,
                                           AtomicInteger sequence,
                                           Throwable error) {
        return errorEvent(sessionId, requestId, sequence, error, false);
    }

    private GuideStreamEvent<?> errorEvent(String sessionId,
                                           String requestId,
                                           AtomicInteger sequence,
                                           Throwable error,
                                           boolean customModel) {
        return event("error", sessionId, requestId, sequence,
                error(error instanceof AppException appException ? appException.getCode() : "AGENT_0001",
                        errorMessage(error),
                        customModel));
    }

    private GuideStreamEvent<?> event(String event,
                                      String sessionId,
                                      String requestId,
                                      AtomicInteger sequence,
                                      Object data) {
        return GuideStreamEvent.of(event, sessionId, requestId, sequence.getAndIncrement(), data);
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
                return "自定义模型接口认证失败，请检查模型配置里的 API 地址、密钥和模型名";
            }
            return "模型密钥无效或权限不足，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥";
        }
        if (lower.contains("api key") && (lower.contains("invalid") || lower.contains("not configured"))) {
            if (customModel || !lower.contains("dashscope")) {
                return "自定义模型配置不可用，请检查模型配置里的 API 地址、密钥和模型名";
            }
            return "模型密钥未配置或不可用，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥";
        }
        return message;
    }

    private boolean hasCustomModelConfig(AcademicAgentStreamRequest request) {
        return request != null
                && (StringUtils.hasText(request.getLlmBaseUrl())
                || StringUtils.hasText(request.getLlmApiKey())
                || StringUtils.hasText(request.getLlmModel()));
    }

    private AcademicSessionSummaryDTO toSummary(UserAccount user, AiSession session) {
        AcademicSessionSummaryDTO dto = new AcademicSessionSummaryDTO();
        dto.setSessionId(bearDoctorNativeAgentService.externalConversationId(user.getUserId(), session.getSessionId()));
        dto.setTitle(title(session.getQuestion(), session.getAgentType()));
        dto.setLastMessage(limit(session.getAnswer(), 120));
        dto.setUpdateTime(session.getUpdateTime() == null ? session.getCreateTime() : session.getUpdateTime());
        return dto;
    }

    private AcademicRunDetailResponse.Run toRun(AcademicAgentRun run) {
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

    private AcademicSessionDetailResponse.Message toMessage(String role, String content, LocalDateTime createTime) {
        AcademicSessionDetailResponse.Message message = new AcademicSessionDetailResponse.Message();
        message.setRole(role);
        message.setContent(nullToBlank(content));
        message.setImageUrl("");
        message.setCreateTime(createTime);
        return message;
    }

    private void appendFailureAssistantIfNeeded(String userId,
                                                String sessionId,
                                                List<AcademicSessionDetailResponse.Message> messages) {
        if (messages.isEmpty() || !"USER".equals(messages.get(messages.size() - 1).getRole())) {
            return;
        }
        try {
            List<AcademicAgentRun> runs = academicExecutionLedgerService.queryRuns(userId, sessionId, 1);
            if (runs.isEmpty()) {
                return;
            }
            AcademicAgentRun latestRun = runs.get(0);
            if (!AcademicAgentRun.STATUS_FAILED.equals(latestRun.getStatus())) {
                return;
            }
            LocalDateTime createTime = latestRun.getFinishedAt() == null ? LocalDateTime.now() : latestRun.getFinishedAt();
            messages.add(toMessage("ASSISTANT", failureAnswer(latestRun), createTime));
        } catch (Exception ignored) {
        }
    }

    private String failureAnswer(AcademicAgentRun run) {
        String partialAnswer = academicArtifactService.sanitizeLocalPaths(nullToBlank(run.getFinalSummary()).trim());
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

    private List<AcademicSessionDetailResponse.Reference> parseReferences(String referenceJson) {
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
            List<AcademicSessionDetailResponse.Reference> references = new ArrayList<>();
            for (JsonNode item : content) {
                AcademicSessionDetailResponse.Reference reference = new AcademicSessionDetailResponse.Reference();
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
            case "skills" -> "skills";
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

    private String normalizeQuery(AcademicAgentStreamRequest request, String taskType) {
        String question = request == null ? "" : nullToBlank(request.getQuestion()).trim();
        if ("ppt".equals(taskType)) {
            return normalizePptQuery(question);
        }
        if (StringUtils.hasText(question)) {
            return question;
        }
        if (StringUtils.hasText(request == null ? "" : request.getFileId())) {
            return "请分析这个文件";
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
            case "deep" -> "深度研究";
            case "skills" -> "技能助手";
            default -> "新对话";
        };
    }

    private String modelName(AcademicAgentStreamRequest request) {
        if (request != null && StringUtils.hasText(request.getLlmModel())) {
            return request.getLlmModel().trim();
        }
        return "bear-doctor-agent";
    }

    private String content(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        return content.isTextual() ? content.asText() : content.toString();
    }

    private String jsonOrText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            return value.isTextual() ? value.asText("") : value.toString();
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean isFailureStatus(String status) {
        String text = nullToBlank(status).toLowerCase();
        return text.contains("fail") || text.contains("error");
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.asInt(fallback);
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.asLong(fallback);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
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
        private final AcademicAgentRun run;
        private final AcademicLedgerContext.Context ledgerContext;
        private final String question;
        private final String modelName;
        private final long startedAt;
        private final boolean webSearchEnabled;
        private final StringBuilder answer = new StringBuilder();
        private final Map<String, String> toolInvocations = new LinkedHashMap<>();

        private RunState(AcademicAgentRun run,
                         AcademicLedgerContext.Context ledgerContext,
                         String question,
                         String modelName,
                         long startedAt,
                         boolean webSearchEnabled) {
            this.run = run;
            this.ledgerContext = ledgerContext;
            this.question = question;
            this.modelName = modelName;
            this.startedAt = startedAt;
            this.webSearchEnabled = webSearchEnabled;
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
