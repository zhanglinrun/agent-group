package com.linrun.trigger.agent.agent.file;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.entity.event.AgentStreamEvent;
import com.linrun.trigger.agent.entity.record.RoundMode;
import com.linrun.trigger.agent.entity.record.RoundState;
import com.linrun.trigger.agent.utils.ThinkTagParser;
import com.linrun.trigger.agent.prompts.ReactAgentPrompts;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.trigger.agent.service.FileManageService;
import com.linrun.trigger.agent.service.MinioService;
import com.linrun.trigger.agent.tool.FileContentService;
import com.linrun.trigger.agent.utils.AppContextClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.linrun.trigger.agent.service.FileManageService.generateObjectName;

/**
 * 文件问答智能??
 * 基于文件内容进行问答分析
 * 支持多种文件类型：PDF、DOC、DOCX、TXT、PNG、JPG??
 */
@Slf4j
public class FileReactAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private String currentFileId;

    private boolean enableRecommendations = false;

    public FileReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools,
                          String systemPrompt, int maxRounds, ChatMemory chatMemory,
                          AiSessionService sessionService, AgentTaskManager taskManager) {
        super(name, chatModel, "file");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集??
        this.usedTools = new HashSet<>();

        initChatClient();

        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    private void initChatClient() {
        try {
            OpenAiChatOptions toolOptions = OpenAiChatOptions.builder()
                    .temperature(0.2d)
                    .parallelToolCalls(false)
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            ChatClient.Builder builder = ChatClient.builder(chatModel);
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    /**
     * 设置当前处理的文件ID
     */
    public void setCurrentFileId(String fileId) {
        this.currentFileId = fileId;
    }

    /**
     * 获取当前文件ID
     */
    public String getCurrentFileId() {
        return currentFileId;
    }

    /**
     * 流式输出（带文件ID??
     */
    public Flux<String> stream(String conversationId, String question, String fileId) {
        setCurrentFileId(fileId);
        return streamInternal(conversationId, question);
    }

    /**
     * 内部流式处理方法
     */
    private Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();

        // ===== 加载 System Prompt（始终放在最开始）=====
        messages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        // ===== 加载文件内容或文件信??=====
        // 注释掉原有的 loadFileContent 调用，使用新??FileContentService 工具替代
        // UserMessage userMessage = loadFileContent();
        // if (userMessage != null && StringUtils.hasText(userMessage.getText()))
        //     messages.add(userMessage);;

        String attachmentContext = buildAttachmentContext(question);
        if (StringUtils.hasText(attachmentContext)) {
            messages.add(new UserMessage(attachmentContext));
        }
        messages.add(new UserMessage("<question>" + question + "</question>"));
        messages.add(new UserMessage("<fileids>" + currentFileId + "</fileids>"));
        currentQuestion = question;

        // 添加记忆并保存到数据??
        if (sessionService != null) {
            // 保存用户问题到数据库，关联fileid
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .fileid(currentFileId)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        // 是否发送最终结果标记位
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 收集最终答案，存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过??
        StringBuilder thinkingBuffer = new StringBuilder();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, thinkingBuffer);

        return sink.asFlux()
                // 分离收集 text ??thinking
                .doOnNext(chunk -> {
                    try {
                        JSONObject json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼??
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    if(taskManager != null){
                        taskManager.stopTask(conversationId);
                    }
                })
                .doFinally(signalType -> {
                    log.info("最终答案 {}", finalAnswerBuffer);
                    log.info("思考过程 {}", thinkingBuffer);

                    // 保存结果到会??
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer);

                    // 流正常结束时只移除任务状态，用户点击停止才发送停止消??
                    if(taskManager != null){
                        taskManager.completeTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    private String buildAttachmentContext(String question) {
        List<String> fileIds = currentFileIds();
        if (fileIds.isEmpty()) {
            return "";
        }
        try {
            FileContentService fileContentService = AppContextClient.getBean(FileContentService.class);
            StringBuilder context = new StringBuilder("以下是系统已经读取到的本轮附件内容，请优先基于这些附件回答用户问题；不要在最终回答中透露文件ID。\n");
            for (int i = 0; i < fileIds.size(); i++) {
                String fileId = fileIds.get(i);
                String content = fileContentService.loadContent(fileId, question);
                context.append("\n--- 附件 ").append(i + 1).append(" ---\n");
                context.append(content == null ? "" : content.trim()).append("\n");
            }
            return context.toString();
        } catch (Exception e) {
            log.warn("加载附件上下文失败 {}", e.getClass().getSimpleName());
            return "";
        }
    }

    private List<String> currentFileIds() {
        if (!StringUtils.hasText(currentFileId)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String fileId : currentFileId.split("[,，\\s]+")) {
            String trimmed = fileId == null ? "" : fileId.trim();
            if (StringUtils.hasText(trimmed) && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 加载文件内容并构建用户消??
     */
    private UserMessage loadFileContent() {
        // 1. 获取依赖服务
        FileManageService fileManageService = AppContextClient.getBean(FileManageService.class);
        MinioService minioService = AppContextClient.getBean(MinioService.class);

        // 2. 查询文件信息
        FileInfo fileInfo = fileManageService.getFileInfo(currentFileId);
        if (fileInfo == null) {
            return UserMessage.builder().text("文件信息不存在，请检查文件ID是否正确").build();
        }

        if (isImageFile(fileInfo.getFileType())) {
            return handleImageFile(fileInfo, minioService);
        } else {
            return handleTextFile(fileInfo);
        }
    }

    /**
     * 处理图片文件
     */
    private UserMessage handleImageFile(FileInfo fileInfo, MinioService minioService) {
        String fileId = fileInfo.getFileId();
        String fileType = fileInfo.getFileType();

        try {
            // 生成MinIO对象名称
            String objectName = generateObjectName(fileId, fileType);

            try (InputStream inputStream = minioService.downloadFile(objectName)) {
                // 读取文件字节数组
                byte[] fileBytes = IOUtils.toByteArray(inputStream);

                // 校验文件字节是否为空
                if (fileBytes == null || fileBytes.length == 0) {
                    return UserMessage.builder().text("图片文件内容为空，请检查文件是否上传完成").build();
                }

                // 构建图片消息
                ByteArrayResource imageResource = new ByteArrayResource(fileBytes);
                List<Media> mediaList = Collections.singletonList(
                        new Media(resolveImageMimeType(fileType), imageResource)
                );

                return UserMessage.builder()
                        .text("当前文件是一张图片，请围绕这个文件进行问答：")
                        .media(mediaList)
                        .build();
            }
        } catch (Exception e) {
            return UserMessage.builder().text("图片文件处理失败：" + e.getMessage()).build();
        }
    }

    /**
     * 处理文本文件
     */
    private UserMessage handleTextFile(FileInfo fileInfo) {
        String extractedText = fileInfo.getExtractedText();
        // 校验文本内容是否为空，提升用户体??
        String textContent = (extractedText == null || extractedText.trim().isEmpty())
                ? "当前文件是一个文本文件，但文件内容为空，请检查文件是否有效"
                : "当前文件是一个文本文件，请围绕这个文件进行问答，以下是这个文件的具体内容：\n" + extractedText;

        return UserMessage.builder()
                .text(textContent)
                .build();
    }

    /**
     * 判断是否为图片文??
     */
    private boolean isImageFile(String fileType) {
        return ("jpg".equalsIgnoreCase(fileType) ||
                "jpeg".equalsIgnoreCase(fileType) ||
                "png".equalsIgnoreCase(fileType) ||
                "gif".equalsIgnoreCase(fileType) ||
                "bmp".equalsIgnoreCase(fileType) ||
                "webp".equalsIgnoreCase(fileType));
    }

    private org.springframework.util.MimeType resolveImageMimeType(String fileType) {
        String lowerType = StringUtils.hasText(fileType) ? fileType.toLowerCase(Locale.ROOT) : "";
        if ("jpg".equals(lowerType) || "jpeg".equals(lowerType)) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if ("gif".equals(lowerType)) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if ("webp".equals(lowerType)) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        return MimeTypeUtils.IMAGE_PNG;
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink,
                               AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId, StringBuilder thinkingBuffer) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, thinkingBuffer))
                .doOnError(err -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(err);
                    }
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {
        if (chunk == null || chunk.getResult() == null ||
                chunk.getResult().getOutput() == null) {
            return;
        }

        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        // 一旦发??tool_call，立即进??TOOL_CALL 模式
        if (tc != null && !tc.isEmpty()) {
            state.setMode(RoundMode.TOOL_CALL);

            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 还没出现 tool_call，使??ThinkTagParser 解析 <think/> 标签
        if (text != null) {
            ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
            state.inThink = parseResult.inThink();
            for (ThinkTagParser.Segment segment : parseResult.segments()) {
                if (segment.thinking()) {
                    sink.tryEmitNext(createThinkingResponse(segment.content()));
                } else {
                    sink.tryEmitNext(createTextResponse(segment.content()));
                    state.getTextBuffer().append(segment.content());
                }
            }
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");
                state.getToolCalls().set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // 新的 toolcall
        state.getToolCalls().add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink,
                             RoundState state, AtomicLong roundCounter,
                             AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer,
                             boolean useMemory, String conversationId, StringBuilder thinkingBuffer) {

        // 如果整轮都没??tool_call，才是最终答??
        if (state.getMode() != RoundMode.TOOL_CALL) {
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);

            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String finalText = state.getTextBuffer().toString();

            // 输出推荐问题
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    currentRecommendations = recommendations; // 保存用于数据库存??
                    String recommendJson = createRecommendResponse(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), state.getToolCalls());
        messages.add(assistantMsg);

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, state, conversationId, useMemory, thinkingBuffer);
            return;
        }

        executeToolCalls(sink, state.getToolCalls(), messages, hasSentFinalResult, state, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult, RoundState state,
                                  String conversationId, boolean useMemory, StringBuilder thinkingBuffer) {
        // 创建新的消息列表，确保系统提示词在最前面
        List<Message> newMessages = new ArrayList<>();

        // 添加系统提示??
        newMessages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));
        if (StringUtils.hasText(systemPrompt)) {
            newMessages.add(new SystemMessage(systemPrompt));
        }

        // 添加原有消息（跳过系统消息）
        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                newMessages.add(msg);
            }
        }

        // 添加限制提示
        newMessages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 替换原消息列??
        messages.clear();
        messages.addAll(newMessages);

        // 收集最终文??
        StringBuilder finalTextBuffer = new StringBuilder();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult()
                            .getOutput()
                            .getText();

                    if (text != null && !hasSentFinalResult.get()) {
                        ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
                        state.inThink = parseResult.inThink();
                        for (ThinkTagParser.Segment segment : parseResult.segments()) {
                            if (segment.thinking()) {
                                sink.tryEmitNext(createThinkingResponse(segment.content()));
                            } else {
                                sink.tryEmitNext(createTextResponse(segment.content()));
                                finalTextBuffer.append(segment.content());
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    String finalText = finalTextBuffer.toString();

                    // 输出推荐问题
                    if (enableRecommendations) {
                        String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations; // 保存用于数据库存??
                            String recommendJson = createRecommendResponse(recommendations);
                            sink.tryEmitNext(recommendJson);
                        }
                    }

                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(err);
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls, List<Message> messages,
                                  AtomicBoolean hasSentFinalResult, RoundState state, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        // 保证顺序一致??
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();
                sink.tryEmitNext(new AgentStreamEvent.ToolStart(toolName, tc.id(), argsJson).toJSON());

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    String errorResult = JSON.toJSONString(Map.of("error", "工具未找到：" + toolName));
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), errorResult).toJSON());
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, errorResult));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                // 如果??loadContent 工具，解析参数并发??thinking 消息
                if (toolName.contains("loadContent")) {
                    JSONObject args = JSON.parseObject(argsJson);
                    String question = (String) args.get("question");
                    // 发??thinking 消息，表示正在加载文件内??
                    String loadThink = "📂 正在检索文件内容，请稍候...";
                    sink.tryEmitNext(createThinkingResponse(loadThink));
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");

                    // 记录使用的工??
                    recordUsedTool(toolName);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), resultStr).toJSON());

                    // 将结果放??responseMap，key ??toolCall.id()
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, resultStr));
                } catch (Exception ex) {
                    String errorResult = JSON.toJSONString(Map.of("error", "工具执行失败：" + Objects.toString(ex.getMessage(), "")));
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), errorResult).toJSON());
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, errorResult));
                } finally {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
            });
        }
    }

    private void completeToolCall(AtomicInteger completedCount, int total,
                                  Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                  List<AssistantMessage.ToolCall> originalToolCalls,
                                  List<Message> messages,
                                  Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            // 按原??toolCalls 的顺序重组结??
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : originalToolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    // 如果某个工具调用没有响应，添加一个错误响??
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"工具响应丢失\" }"));
                }
            }

            // 一次性添加所有工具响应（按原始顺序）
            messages.add(new ToolResponseMessage(sortedResponses));

            onComplete.run();
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(new ToolResponseMessage(List.of(tr)));
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;
        private String systemPrompt = "";
        private int maxRounds;
        private ChatMemory chatMemory;
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder sessionService(AiSessionService sessionService) {
            this.sessionService = sessionService;
            return this;
        }

        public Builder taskManager(AgentTaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public FileReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空");
            }
            return new FileReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, sessionService, taskManager);
        }
    }
}
















