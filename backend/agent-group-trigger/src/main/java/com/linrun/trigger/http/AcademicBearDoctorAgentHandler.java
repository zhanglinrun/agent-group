package com.linrun.trigger.http;

import cn.hollis.llm.mentor.agent.entity.AiSession;
import cn.hollis.llm.mentor.agent.entity.record.FileInfo;
import cn.hollis.llm.mentor.agent.entity.record.pptx.AiPptInst;
import cn.hollis.llm.mentor.agent.service.AgentTaskManager;
import cn.hollis.llm.mentor.agent.service.AiPptInstService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.api.dto.AcademicFileUploadResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AcademicSessionSummaryDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
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
    private final ObjectMapper objectMapper;

    public AcademicBearDoctorAgentHandler(BearDoctorNativeAgentService bearDoctorNativeAgentService,
                                    UserAccountService userAccountService,
                                    UserQuotaService userQuotaService,
                                    AgentTaskManager taskManager,
                                    AiPptInstService aiPptInstService,
                                    AcademicBackgroundStreamService backgroundStreamService,
                                    AcademicArtifactService academicArtifactService,
                                    ObjectMapper objectMapper) {
        this.bearDoctorNativeAgentService = bearDoctorNativeAgentService;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.taskManager = taskManager;
        this.aiPptInstService = aiPptInstService;
        this.backgroundStreamService = backgroundStreamService;
        this.academicArtifactService = academicArtifactService;
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
            long startedAt = System.currentTimeMillis();
            AtomicInteger sequence = new AtomicInteger(1);

            return bearDoctorNativeAgentService.stream(token, taskType, query, sessionId, fileId,
                            safeRequest.getLlmBaseUrl(), safeRequest.getLlmApiKey(), safeRequest.getLlmModel())
                    .flatMapIterable(raw -> toEvents(raw, sessionId, requestId, sequence))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(completionEvents(user, sessionId, requestId, sequence, taskType, startedAt))))
                    .onErrorResume(error -> Flux.just(errorEvent(sessionId, requestId, sequence, error, hasCustomModelConfig(safeRequest))));
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
                messages.add(toMessage("ASSISTANT", academicArtifactService.sanitizeLocalPaths(session.getAnswer()), session.getUpdateTime()));
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
        response.setMessages(messages);
        return response;
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
                                               AtomicInteger sequence) {
        if (!StringUtils.hasText(raw) || "[DONE]".equals(raw.trim())) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            String type = text(node, "type");
            return switch (type) {
                case "text" -> List.of(event("answer_delta", sessionId, requestId, sequence,
                        Map.of("content", academicArtifactService.sanitizeLocalPaths(content(node)))));
                case "thinking" -> List.of(event("task_status", sessionId, requestId, sequence,
                        status("THINKING", content(node))));
                case "tool_start" -> List.of(event("task_status", sessionId, requestId, sequence,
                        status("TOOL", "开始调用工具：" + text(node, "toolName"))));
                case "tool_end" -> List.of(event("task_status", sessionId, requestId, sequence,
                        status("TOOL", "工具调用完成：" + text(node, "toolName"))));
                case "reference" -> referenceEvents(node, sessionId, requestId, sequence);
                case "recommend" -> List.of(event("recommend_delta", sessionId, requestId, sequence, recommend(node)));
                case "error" -> List.of(event("error", sessionId, requestId, sequence,
                        error(text(node, "code"), firstText(node, "message", "content", "detail"))));
                case "complete" -> List.of(event("done", sessionId, requestId, sequence, "done"));
                default -> List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", raw)));
            };
        } catch (Exception e) {
            return List.of(event("answer_delta", sessionId, requestId, sequence, Map.of("content", raw)));
        }
    }

    private List<GuideStreamEvent<?>> completionEvents(UserAccount user,
                                                       String sessionId,
                                                       String requestId,
                                                       AtomicInteger sequence,
                                                       String taskType,
                                                       long startedAt) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        if ("ppt".equals(taskType)) {
            pptArtifact(user, sessionId).ifPresent(artifact ->
                    events.add(event("artifact_delta", sessionId, requestId, sequence, artifact)));
        }
        if ("skills".equals(taskType)) {
            for (AcademicSessionDetailResponse.Artifact artifact :
                    academicArtifactService.collectAndSave(user.getUserId(), sessionId, startedAt)) {
                events.add(event("artifact_delta", sessionId, requestId, sequence,
                        academicArtifactService.toEventPayload(artifact)));
            }
        }
        QuotaAccountResponse quota = userQuotaService.queryAccountResponse(user.getUserId());
        events.add(event("quota_delta", sessionId, requestId, sequence, quota));
        events.add(event("usage_metric", sessionId, requestId, sequence, Map.of(
                "consumedQuota", userQuotaService.estimatePreCheckCost(taskType),
                "remainingQuota", quota.getQuotaBalance(),
                "model", "bear-doctor-agent")));
        events.add(event("done", sessionId, requestId, sequence, "done"));
        return events;
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
                        error == null ? "处理失败" : error.getMessage(),
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

    private String normalizeErrorMessage(String message, boolean customModel) {
        if (!StringUtils.hasText(message)) {
            return "处理失败";
        }
        String lower = message.toLowerCase();
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "本次请求已处理，请勿重复提交或刷新后重试";
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

    private AcademicSessionDetailResponse.Message toMessage(String role, String content, LocalDateTime createTime) {
        AcademicSessionDetailResponse.Message message = new AcademicSessionDetailResponse.Message();
        message.setRole(role);
        message.setContent(nullToBlank(content));
        message.setImageUrl("");
        message.setCreateTime(createTime);
        return message;
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

    private String content(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        return content.isTextual() ? content.asText() : content.toString();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
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

    private String limit(String value, int maxLength) {
        String safe = nullToBlank(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String internalSessionId(String userId, String sessionId) {
        return userId + ":" + nullToBlank(sessionId).trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
