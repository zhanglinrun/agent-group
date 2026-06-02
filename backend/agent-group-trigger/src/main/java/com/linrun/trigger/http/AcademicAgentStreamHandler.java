package com.linrun.trigger.http;

import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AcademicSessionSummaryDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.api.dto.GuideUsageMetricsDTO;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.adapter.AgentStreamTaskRegistry;
import com.linrun.domain.agent.conversation.adapter.GuideLlmClient;
import com.linrun.domain.agent.conversation.model.GuideLlmResult;
import com.linrun.domain.agent.conversation.model.GuideRagPrompt;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class AcademicAgentStreamHandler {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AcademicAgentRepository academicAgentRepository;
    private final GuideLlmClient guideLlmClient;
    private final AgentStreamTaskRegistry streamTaskRegistry;
    private final KnowledgeVectorService knowledgeVectorService;
    private final AcademicExternalSearchService externalSearchService;

    public AcademicAgentStreamHandler(UserAccountService userAccountService,
                                      UserQuotaService userQuotaService,
                                      AcademicAgentRepository academicAgentRepository,
                                      GuideLlmClient guideLlmClient,
                                      AgentStreamTaskRegistry streamTaskRegistry,
                                      KnowledgeVectorService knowledgeVectorService,
                                      AcademicExternalSearchService externalSearchService) {
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.academicAgentRepository = academicAgentRepository;
        this.guideLlmClient = guideLlmClient;
        this.streamTaskRegistry = streamTaskRegistry == null ? AgentStreamTaskRegistry.noop() : streamTaskRegistry;
        this.knowledgeVectorService = knowledgeVectorService;
        this.externalSearchService = externalSearchService;
    }

    public Flux<GuideStreamEvent<?>> streamEventFlux(String token,
                                                     AcademicAgentStreamRequest request,
                                                     String sessionId,
                                                     String requestId,
                                                     BooleanSupplier stopped) {
        UserAccount user = userAccountService.requireUserByToken(token);
        AcademicAgentStreamRequest safeRequest = request == null ? new AcademicAgentStreamRequest() : request;
        if (!StringUtils.hasText(safeRequest.getQuestion()) && !StringUtils.hasText(safeRequest.getFileId())) {
            return Flux.just(GuideStreamEvent.of("error", sessionId, requestId, 1,
                    error("0001", "问题或文件不能为空")));
        }
        String taskType = taskType(safeRequest);
        userQuotaService.assertEnoughQuota(user.getUserId(), userQuotaService.estimatePreCheckCost(taskType));

        if (!streamTaskRegistry.register(sessionId, requestId)) {
            return Flux.just(GuideStreamEvent.of("error", sessionId, requestId, 1,
                    error("AGENT_0006", "当前会话已有任务正在执行")));
        }

        Sinks.Many<GuideStreamEvent<?>> streamSink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Disposable disposable = Schedulers.boundedElastic().schedule(() -> {
            try {
                streamEvents(user, safeRequest, sessionId, requestId, event -> streamSink.tryEmitNext(event),
                        () -> cancelled.get() || (stopped != null && stopped.getAsBoolean()));
                if (!cancelled.get()) {
                    streamSink.tryEmitComplete();
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    streamSink.tryEmitNext(GuideStreamEvent.of("error", sessionId, requestId, 999,
                            error(e instanceof AppException appException ? appException.getCode() : "AGENT_0001",
                                    e.getMessage())));
                    streamSink.tryEmitComplete();
                }
            }
        });
        streamTaskRegistry.bind(sessionId, requestId, () -> {
            cancelled.set(true);
            disposable.dispose();
            streamSink.tryEmitComplete();
        });
        return streamSink.asFlux()
                .doOnCancel(() -> cancelled.set(true))
                .doFinally(signalType -> streamTaskRegistry.complete(sessionId, requestId));
    }

    public List<AcademicSessionSummaryDTO> querySessions(String token, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return academicAgentRepository.querySessions(user.getUserId(), Math.max(1, Math.min(limit, 50)))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public AcademicSessionDetailResponse queryDetail(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        AcademicSessionDetailResponse response = new AcademicSessionDetailResponse();
        response.setSessionId(sessionId);
        response.setMessages(academicAgentRepository.queryMessages(user.getUserId(), sessionId)
                .stream()
                .map(this::toMessage)
                .toList());
        return response;
    }

    public Map<String, Object> queryTaskStatus(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        boolean exists = academicAgentRepository.querySession(user.getUserId(), sessionId).isPresent();
        int messageCount = exists ? academicAgentRepository.queryMessages(user.getUserId(), sessionId).size() : 0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("running", streamTaskRegistry.isRunning(sessionId));
        data.put("stopped", false);
        data.put("exists", exists);
        data.put("messageCount", messageCount);
        data.put("resumable", exists && !streamTaskRegistry.isRunning(sessionId) && messageCount > 0);
        return data;
    }

    public AcademicAgentStreamRequest resumeRequest(String token, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        AcademicSession session = academicAgentRepository.querySession(user.getUserId(), sessionId)
                .orElseThrow(() -> new AppException("SESSION_0001", "会话不存在"));
        AcademicMessage latestUserMessage = latestUserMessage(user.getUserId(), sessionId);
        AcademicAgentStreamRequest request = new AcademicAgentStreamRequest();
        request.setSessionId(sessionId);
        request.setTaskType(session.getTaskType());
        request.setQuestion("""
                请从上次中断处继续完成这个任务，避免重复已经完成的内容。

                原始问题：
                %s
                """.formatted(latestUserMessage.getContent()));
        academicAgentRepository.queryFilesBySession(user.getUserId(), sessionId).stream()
                .findFirst()
                .ifPresent(file -> request.setFileId(file.getFileId()));
        return request;
    }

    private void streamEvents(UserAccount user,
                              AcademicAgentStreamRequest request,
                              String sessionId,
                              String requestId,
                              Consumer<GuideStreamEvent<?>> sink,
                              BooleanSupplier stopped) {
        long startNanos = System.nanoTime();
        AtomicInteger sequence = new AtomicInteger(1);
        String taskType = taskType(request);
        AcademicFile file = resolveFile(user.getUserId(), request.getFileId());
        saveSessionAndUserMessage(user, request, sessionId, taskType);

        if (!emit(sink, stopped, sessionId, requestId, sequence, "task_status",
                status("PLAN", "正在分析任务类型：" + taskLabel(taskType)))) {
            return;
        }
        if (file != null && !emit(sink, stopped, sessionId, requestId, sequence, "reference_delta",
                reference(file))) {
            return;
        }
        if (file != null && !emit(sink, stopped, sessionId, requestId, sequence, "task_status",
                status("RETRIEVE", "正在检索文件向量片段"))) {
            return;
        }
        List<KnowledgeFragment> vectorReferences = retrieveVectorReferences(request, file);
        for (KnowledgeFragment fragment : vectorReferences) {
            if (!emit(sink, stopped, sessionId, requestId, sequence, "reference_delta", reference(fragment))) {
                return;
            }
        }

        List<AcademicExternalSearchService.SearchReference> searchReferences = List.of();
        if (shouldUseExternalSearch(taskType, request)) {
            if (!emit(sink, stopped, sessionId, requestId, sequence, "task_status",
                    status("SEARCH", externalSearchService.available() ? "正在进行外部搜索" : "外部搜索未配置，跳过联网检索"))) {
                return;
            }
            searchReferences = externalSearchService.search(request.getQuestion(), 4);
            for (AcademicExternalSearchService.SearchReference reference : searchReferences) {
                if (!emit(sink, stopped, sessionId, requestId, sequence, "reference_delta", reference(reference))) {
                    return;
                }
            }
        }

        StringBuilder answerBuffer = new StringBuilder();
        GuideRagPrompt prompt = prompt(request, taskType, file, vectorReferences, searchReferences);
        GuideLlmResult result = guideLlmClient.streamWithMetrics(prompt, chunk -> {
            answerBuffer.append(chunk);
            emit(sink, stopped, sessionId, requestId, sequence, "answer_delta", Map.of("content", chunk));
        }, stopped);
        if (stopped.getAsBoolean()) {
            return;
        }

        String answer = StringUtils.hasText(answerBuffer) ? answerBuffer.toString() : result.getContent();
        saveAssistantMessage(user.getUserId(), sessionId, answer);
        AcademicArtifact artifact = createArtifactIfNeeded(user.getUserId(), sessionId, taskType, request, answer);
        if (artifact != null) {
            academicAgentRepository.saveArtifact(artifact);
            if (!emit(sink, stopped, sessionId, requestId, sequence, "artifact_delta", artifact(artifact))) {
                return;
            }
        }

        QuotaAccountResponse quota = userQuotaService.consumeForAcademicTask(
                user.getUserId(),
                sessionId,
                taskType,
                result.getTokenUsage(),
                result.getModel(),
                result.getLatencyMillis());
        if (!emit(sink, stopped, sessionId, requestId, sequence, "quota_delta", quota)) {
            return;
        }
        emit(sink, stopped, sessionId, requestId, sequence, "usage_metric",
                usage(result, quota, taskType, elapsedMillis(startNanos)));
        emit(sink, stopped, sessionId, requestId, sequence, "done", "done");
    }

    private AcademicFile resolveFile(String userId, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return academicAgentRepository.queryFile(userId, fileId)
                .orElseThrow(() -> new AppException("FILE_0001", "文件不存在或无权访问"));
    }

    private AcademicMessage latestUserMessage(String userId, String sessionId) {
        return academicAgentRepository.queryMessages(userId, sessionId).stream()
                .filter(message -> ROLE_USER.equals(message.getRole()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AppException("SESSION_0002", "会话内没有可继续的问题"));
    }

    private void saveSessionAndUserMessage(UserAccount user,
                                           AcademicAgentStreamRequest request,
                                           String sessionId,
                                           String taskType) {
        LocalDateTime now = LocalDateTime.now();
        AcademicSession session = new AcademicSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getUserId());
        session.setTitle(title(request.getQuestion(), taskType));
        session.setTaskType(taskType);
        session.setLastMessage(limit(request.getQuestion(), 240));
        session.setCreateTime(now);
        session.setUpdateTime(now);
        academicAgentRepository.saveSessionIfAbsent(session);
        academicAgentRepository.updateSession(session);

        AcademicMessage message = new AcademicMessage();
        message.setMessageId(nextNo("AM"));
        message.setSessionId(sessionId);
        message.setUserId(user.getUserId());
        message.setRole(ROLE_USER);
        message.setContent(StringUtils.hasText(request.getQuestion()) ? request.getQuestion() : "上传文件并请求处理");
        message.setImageUrl(nullToBlank(request.getImageUrl()));
        message.setCreateTime(now);
        academicAgentRepository.saveMessage(message);
    }

    private void saveAssistantMessage(String userId, String sessionId, String answer) {
        AcademicMessage message = new AcademicMessage();
        message.setMessageId(nextNo("AM"));
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent(limit(answer, 60000));
        message.setImageUrl("");
        message.setCreateTime(LocalDateTime.now());
        academicAgentRepository.saveMessage(message);
    }

    private GuideRagPrompt prompt(AcademicAgentStreamRequest request,
                                  String taskType,
                                  AcademicFile file,
                                  List<KnowledgeFragment> vectorReferences,
                                  List<AcademicExternalSearchService.SearchReference> searchReferences) {
        GuideRagPrompt prompt = new GuideRagPrompt();
        prompt.setSystemPrompt(systemPrompt(taskType));
        prompt.setUserPrompt(userPrompt(request, taskType, file, vectorReferences, searchReferences));
        prompt.setFallbackAnswer(fallbackAnswer(taskType, request, file));
        return prompt;
    }

    private String systemPrompt(String taskType) {
        return switch (taskType == null ? "" : taskType.toLowerCase()) {
            case "paper", "file" -> """
                    你是面向研究生的文件问答 Agent。请优先依据用户上传的文件内容回答。
                    输出要包含：核心结论、依据片段、可执行建议。文件里没有的信息要明确说明。
                    """;
            case "ppt" -> """
                    你是 PPT 生成 Agent。请按 dodo-agent 的流程工作：需求澄清、资料整理、页面大纲、结构化页面方案。
                    输出要适合组会汇报、开题答辩或论文分享，内容保持可编辑、可继续生成幻灯片。
                    """;
            case "deep-research", "deep" -> """
                    你是深度研究 Agent。请先拆解任务，再给出分步骤研究计划、关键资料线索、判断依据和最终结论。
                    不要编造实时搜索结果；如果需要外部资料，请说明建议检索的关键词和验证方式。
                    """;
            case "diagram", "image-to-diagram" -> """
                    你是图表重建 Agent。请把用户的描述、图片或文件内容整理为可编辑图表方案。
                    优先输出 Mermaid、流程节点、模块说明和可修改建议。
                    """;
            case "skills" -> """
                    你是技能助手 Agent。请先判断任务应使用哪类技能，再按技能步骤输出结果。
                    可用技能包括：论文精读、PPT 规划、图表重建、深度研究、代码说明、实验方案整理。
                    """;
            default -> """
                    你是面向研究生的学术创作 Agent。请用中文回答，输出要结构化、可执行。
                    你可以帮助论文阅读、实验方案规划、PPT 大纲生成、流程图/架构图重建和深度研究。
                    不要声称已经完成真实支付、真实外部下载或无法验证的事实。
                    """;
        };
    }

    private String userPrompt(AcademicAgentStreamRequest request,
                              String taskType,
                              AcademicFile file,
                              List<KnowledgeFragment> vectorReferences,
                              List<AcademicExternalSearchService.SearchReference> searchReferences) {
        return """
                【任务类型】
                %s

                【用户问题】
                %s

                【文件内容摘要】
                %s

                【文件向量检索片段】
                %s

                【外部搜索资料】
                %s

                【图片输入】
                %s

                【输出要求】
                %s
                """.formatted(taskLabel(taskType), nullToBlank(request.getQuestion()), file == null ? "无" : limit(file.getContent(), 12000),
                vectorDigest(vectorReferences), searchDigest(searchReferences),
                StringUtils.hasText(request.getImageUrl()) ? request.getImageName() : "无", outputRequirement(taskType));
    }

    private String outputRequirement(String taskType) {
        return switch (taskType == null ? "" : taskType.toLowerCase()) {
            case "ppt" -> "给出 5-8 页 PPT 的页面标题、每页要点、讲稿提示和可生成的 JSON 结构草稿。";
            case "deep-research", "deep" -> "先列研究计划，再分点整理证据、结论、风险和下一步验证动作。";
            case "paper", "file" -> "围绕文件内容回答，包含摘要、关键依据、可能问题和后续阅读建议。";
            case "diagram", "image-to-diagram" -> "给出 Mermaid 草稿，并解释每个节点和连线含义。";
            case "skills" -> "先说明选择的技能，再输出步骤化结果，最后给出可继续追问的问题。";
            default -> "回答要简洁但信息完整，尽量给出可直接使用的结果。";
        };
    }

    private AcademicArtifact createArtifactIfNeeded(String userId,
                                                    String sessionId,
                                                    String taskType,
                                                    AcademicAgentStreamRequest request,
                                                    String answer) {
        String normalized = taskType.toLowerCase();
        if (!normalized.contains("ppt")
                && !normalized.contains("diagram")
                && !normalized.contains("image")
                && !normalized.contains("skills")) {
            return null;
        }
        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId(nextNo("AA"));
        artifact.setSessionId(sessionId);
        artifact.setUserId(userId);
        artifact.setCreateTime(LocalDateTime.now());
        if (normalized.contains("ppt")) {
            artifact.setArtifactType("PPT_SCHEMA");
            artifact.setTitle("学术演示稿结构");
            artifact.setContent(pptSchema(request.getQuestion(), answer));
        } else if (normalized.contains("skills")) {
            artifact.setArtifactType("SKILL_PLAN");
            artifact.setTitle("技能执行计划");
            artifact.setContent(skillPlan(request.getQuestion(), answer));
        } else {
            artifact.setArtifactType("MERMAID");
            artifact.setTitle("可编辑流程图草稿");
            artifact.setContent(mermaid(request.getQuestion(), answer));
        }
        artifact.setDownloadUrl("");
        return artifact;
    }

    private String pptSchema(String question, String answer) {
        return """
                {
                  "title": "%s",
                  "slides": [
                    {"type": "cover", "title": "%s"},
                    {"type": "outline", "title": "研究背景与问题定义"},
                    {"type": "method", "title": "方法框架与关键模块"},
                    {"type": "experiment", "title": "实验设计与评测指标"},
                    {"type": "summary", "title": "结论与后续工作"}
                  ],
                  "sourceDigest": "%s"
                }
                """.formatted(jsonEscape(title(question, "ppt")), jsonEscape(title(question, "ppt")), jsonEscape(limit(answer, 300)));
    }

    private String mermaid(String question, String answer) {
        return """
                flowchart LR
                  A[输入资料] --> B[多模态解析]
                  B --> C[结构识别]
                  C --> D[可编辑图表草稿]
                  D --> E[人工修订与导出]
                  note1["%s"]
                """.formatted(mermaidEscape(StringUtils.hasText(question) ? question : limit(answer, 80)));
    }

    private String skillPlan(String question, String answer) {
        return """
                {
                  "task": "%s",
                  "skills": ["任务判断", "资料整理", "结构化输出", "结果复核"],
                  "resultDigest": "%s"
                }
                """.formatted(jsonEscape(title(question, "skills")), jsonEscape(limit(answer, 300)));
    }

    private GuideUsageMetricsDTO usage(GuideLlmResult result,
                                       QuotaAccountResponse quota,
                                       String taskType,
                                       long totalLatencyMillis) {
        GuideTokenUsage tokenUsage = result == null ? GuideTokenUsage.empty() : result.getTokenUsage();
        GuideUsageMetricsDTO dto = new GuideUsageMetricsDTO();
        dto.setPromptTokens(tokenUsage.getPromptTokens());
        dto.setCompletionTokens(tokenUsage.getCompletionTokens());
        dto.setTotalTokens(tokenUsage.getTotalTokens());
        dto.setEstimatedCostYuan(tokenUsage.getEstimatedCostYuan());
        dto.setLlmLatencyMillis(result == null ? 0L : result.getLatencyMillis());
        dto.setTotalLatencyMillis(totalLatencyMillis);
        dto.setFallbackUsed(result != null && result.isFallbackUsed());
        dto.setConsumedQuota(userQuotaService.estimatePreCheckCost(taskType));
        dto.setRemainingQuota(quota.getQuotaBalance());
        dto.setModel(result == null ? "" : result.getModel());
        return dto;
    }

    private <T> boolean emit(Consumer<GuideStreamEvent<?>> sink,
                             BooleanSupplier stopped,
                             String sessionId,
                             String requestId,
                             AtomicInteger sequence,
                             String eventType,
                             T data) {
        if (stopped != null && stopped.getAsBoolean()) {
            return false;
        }
        sink.accept(GuideStreamEvent.of(eventType, sessionId, requestId, sequence.getAndIncrement(), data));
        return stopped == null || !stopped.getAsBoolean();
    }

    private Map<String, Object> status(String stage, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage);
        data.put("message", message);
        return data;
    }

    private Map<String, Object> reference(AcademicFile file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceType", "file");
        data.put("fileId", file.getFileId());
        data.put("title", file.getFileName());
        data.put("content", file.getSummary());
        return data;
    }

    private List<KnowledgeFragment> retrieveVectorReferences(AcademicAgentStreamRequest request, AcademicFile file) {
        if (knowledgeVectorService == null || file == null) {
            return List.of();
        }
        String question = StringUtils.hasText(request.getQuestion()) ? request.getQuestion() : file.getSummary();
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        return knowledgeVectorService.searchSimilar(question, file.getFileId(), 6);
    }

    private boolean shouldUseExternalSearch(String taskType, AcademicAgentStreamRequest request) {
        String normalizedType = taskType == null ? "" : taskType.toLowerCase();
        String question = request == null ? "" : nullToBlank(request.getQuestion()).toLowerCase();
        return normalizedType.contains("deep")
                || normalizedType.contains("ppt")
                || question.contains("最新")
                || question.contains("联网")
                || question.contains("搜索")
                || question.contains("调研")
                || question.contains("2026")
                || question.contains("2027");
    }

    private Map<String, Object> reference(KnowledgeFragment fragment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceType", "vector");
        data.put("fragmentId", fragment.getFragmentId());
        data.put("documentId", fragment.getDocumentId());
        data.put("title", "文件片段 " + (fragment.getRankNo() == null ? "" : fragment.getRankNo()));
        data.put("content", limit(fragment.getContent(), 800));
        return data;
    }

    private Map<String, Object> reference(AcademicExternalSearchService.SearchReference reference) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceType", "search");
        data.put("title", StringUtils.hasText(reference.title()) ? reference.title() : reference.url());
        data.put("url", nullToBlank(reference.url()));
        data.put("content", limit(reference.content(), 800));
        return data;
    }

    private Map<String, Object> artifact(AcademicArtifact artifact) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactId", artifact.getArtifactId());
        data.put("artifactType", artifact.getArtifactType());
        data.put("title", artifact.getTitle());
        data.put("content", artifact.getContent());
        data.put("downloadUrl", artifact.getDownloadUrl());
        return data;
    }

    private Map<String, String> error(String code, String message) {
        return Map.of("code", code, "message", message == null ? "处理失败" : message);
    }

    private AcademicSessionSummaryDTO toSummary(AcademicSession session) {
        AcademicSessionSummaryDTO dto = new AcademicSessionSummaryDTO();
        dto.setSessionId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setLastMessage(session.getLastMessage());
        dto.setUpdateTime(session.getUpdateTime());
        return dto;
    }

    private AcademicSessionDetailResponse.Message toMessage(AcademicMessage message) {
        AcademicSessionDetailResponse.Message dto = new AcademicSessionDetailResponse.Message();
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setImageUrl(message.getImageUrl());
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }

    private String vectorDigest(List<KnowledgeFragment> references) {
        if (references == null || references.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (KnowledgeFragment reference : references) {
            builder.append("- ")
                    .append(reference.getDocumentId())
                    .append("#")
                    .append(reference.getRankNo() == null ? "" : reference.getRankNo())
                    .append("：")
                    .append(limit(reference.getContent(), 600))
                    .append("\n");
        }
        return builder.toString();
    }

    private String searchDigest(List<AcademicExternalSearchService.SearchReference> references) {
        if (references == null || references.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (AcademicExternalSearchService.SearchReference reference : references) {
            builder.append("- ")
                    .append(StringUtils.hasText(reference.title()) ? reference.title() : reference.url())
                    .append(StringUtils.hasText(reference.url()) ? "（" + reference.url() + "）" : "")
                    .append("：")
                    .append(limit(reference.content(), 600))
                    .append("\n");
        }
        return builder.toString();
    }

    private String fallbackAnswer(String taskType, AcademicAgentStreamRequest request, AcademicFile file) {
        return """
                已进入%s模式。当前模型不可用时，系统会先基于已上传资料整理一个可执行草稿：
                1. 明确目标和约束：%s
                2. 提取资料依据：%s
                3. 生成可继续编辑的文字或图表结构。
                """.formatted(taskLabel(taskType), nullToBlank(request.getQuestion()), file == null ? "暂无上传文件" : file.getSummary());
    }

    private String taskType(AcademicAgentStreamRequest request) {
        if (request != null && StringUtils.hasText(request.getTaskType())) {
            return request.getTaskType();
        }
        String question = request == null ? "" : nullToBlank(request.getQuestion()).toLowerCase();
        if (question.contains("技能") || question.contains("skill")) {
            return "skills";
        }
        if (question.contains("ppt") || question.contains("演示") || question.contains("幻灯片")) {
            return "ppt";
        }
        if (question.contains("流程图") || question.contains("架构图") || question.contains("图表") || question.contains("diagram")) {
            return "diagram";
        }
        if (question.contains("论文") || question.contains("pdf") || StringUtils.hasText(request == null ? "" : request.getFileId())) {
            return "paper";
        }
        if (question.contains("调研") || question.contains("研究")) {
            return "deep-research";
        }
        return "chat";
    }

    private String taskLabel(String taskType) {
        return switch (taskType == null ? "" : taskType.toLowerCase()) {
            case "ppt" -> "PPT 生成";
            case "diagram", "image-to-diagram" -> "图表重建";
            case "paper", "file" -> "文件问答";
            case "deep-research", "deep" -> "深度研究";
            case "skills" -> "技能助手";
            default -> "学术对话";
        };
    }

    private String title(String question, String fallback) {
        if (!StringUtils.hasText(question)) {
            return taskLabel(fallback);
        }
        return limit(question.replaceAll("\\s+", " ").trim(), 24);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nextNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String jsonEscape(String value) {
        return nullToBlank(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String mermaidEscape(String value) {
        return nullToBlank(value).replace("\"", "'");
    }
}
