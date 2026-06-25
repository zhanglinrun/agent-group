package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.OverAllState;
import com.linrun.trigger.agent.entity.record.*;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.prompts.PlanExecutePrompts;
import com.linrun.trigger.agent.utils.ThinkTagParser;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiSessionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


@Slf4j
public class PlanExecuteAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;

    // plan-execute 总轮??
    private final int maxRounds;

    // context 压缩阈??
    private final int contextCharLimit;

    // 控制工具并发调用上限
    private final Semaphore toolSemaphore;

    // 工具重试次数
    private final int maxToolRetries;
    private String systemPrompt = "";

    // 用于管理所有需要取消的Disposable
    private Disposable.Composite compositeDisposable;

    // 存储所有搜索结果，用于保存到数据库和发送给前端
    private List<SearchResult> allReferences;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PlanExecuteAgent(ChatModel chatModel,
                            List<ToolCallback> tools,
                            int maxRounds,
                            int contextCharLimit,
                            int maxToolRetries,
                            ChatMemory chatMemory,
                            AiSessionService sessionService,
                            AgentTaskManager taskManager) {
        this(chatModel, tools, maxRounds, contextCharLimit, maxToolRetries, "",
                chatMemory, sessionService, taskManager);
    }

    public PlanExecuteAgent(ChatModel chatModel,
                            List<ToolCallback> tools,
                            int maxRounds,
                            int contextCharLimit,
                            int maxToolRetries,
                            String systemPrompt,
                            ChatMemory chatMemory,
                            AiSessionService sessionService,
                            AgentTaskManager taskManager) {
        super("PlanExecuteAgent", chatModel, "plan-execute");
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tools = tools;
        this.maxRounds = maxRounds;
        this.contextCharLimit = contextCharLimit;
        this.maxToolRetries = maxToolRetries;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.toolSemaphore = new Semaphore(3);
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集??
        this.usedTools = new HashSet<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatModel chatModel;
        private List<ToolCallback> tools = new ArrayList<>();

        // 默认迭代3??
        private int maxRounds = 3;

        // 默认context压缩阈??0000字符
        private int contextCharLimit = 50000;

        // 默认工具重试次数2??
        private int maxToolRetries = 2;

        private ChatMemory chatMemory;

        private AiSessionService sessionService;

        private AgentTaskManager taskManager;

        private String systemPrompt = "";

        public Builder sessionService(AiSessionService sessionService) {
            this.sessionService = sessionService;
            return this;
        }

        public Builder taskManager(AgentTaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder contextCharLimit(int contextCharLimit) {
            this.contextCharLimit = contextCharLimit;
            return this;
        }

        public Builder maxToolRetries(int maxToolRetries) {
            this.maxToolRetries = maxToolRetries;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
            return this;
        }

        public PlanExecuteAgent build() {
            Objects.requireNonNull(chatModel, "chatModel must not be null");
            return new PlanExecuteAgent(chatModel, tools, maxRounds, contextCharLimit, maxToolRetries,
                    systemPrompt, chatMemory, sessionService, taskManager);
        }
    }

    public String call(String question) {
        return callInternal(null, question).blockLast();
    }

    public String call(String conversationId, String question) {
        return callInternal(conversationId, question).blockLast();
    }

    public Flux<String> stream(String question) {
        return callInternal(null, question);
    }

    public Flux<String> stream(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    public Flux<String> callInternal(String conversationId, String question) {

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 初始化状态和缓冲??
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean finished = new AtomicBoolean(false);

        // 注册任务到管理器
        if (!registerTaskInternal(conversationId, sink)) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // 初始化会话信??
        initTimers();
        clearUsedTools();
        currentConversationId = conversationId;
        currentQuestion = question;
        compositeDisposable = Disposables.composite();

        // 创建缓冲??
        StringBuilder finalAnswerBuffer = new StringBuilder();
        StringBuilder thinkingBuffer = new StringBuilder();
        allReferences = new ArrayList<>();

        // 初始化状态并保存问题
        OverAllState state = initStateAndSaveQuestion(conversationId, question);

        // 启动流程：需求澄??-> 研究主题生成 -> 执行循环
        clarifyRequirementPhase(state, sink, finished, thinkingBuffer,
                () -> generateResearchTopicPhase(state, sink, finished, thinkingBuffer,
                        () -> executeLoopPhase(state, sink, finished, finalAnswerBuffer,
                                thinkingBuffer)));

        // 注册任务到管理器
        registerTaskToManager(conversationId);

        return wrapSinkWithHandlers(sink, finished, conversationId, finalAnswerBuffer, thinkingBuffer);
    }

    /**
     * 初始化状态并保存问题到数据库
     */
    private OverAllState initStateAndSaveQuestion(String conversationId, String question) {
        OverAllState state = new OverAllState(conversationId, question);

        // 加载历史消息
        List<Message> history = getChatHistory(conversationId);
        if (CollectionUtils.isNotEmpty(history)) {
            history.forEach(state::add);
        }
        if (StringUtils.hasText(systemPrompt)) {
            state.add(new SystemMessage(systemPrompt));
        }
        state.add(new UserMessage(question));

        // 保存用户问题到数据库
        if (conversationId != null && sessionService != null) {
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .firstResponseTime(firstResponseTime)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        return state;
    }

    /**
     * 注册任务到管理器（内部方法）
     */
    private boolean registerTaskInternal(String conversationId, Sinks.Many<String> sink) {
        if (conversationId == null) {
            return true;
        }
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        return taskInfo != null || taskManager == null;
    }

    /**
     * 注册任务到任务管理器
     */
    private void registerTaskToManager(String conversationId) {
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, compositeDisposable);
        }
    }

    /**
     * 包装 sink 并添加处理逻辑
     */
    private Flux<String> wrapSinkWithHandlers(Sinks.Many<String> sink, AtomicBoolean finished,
                                              String conversationId, StringBuilder finalAnswerBuffer,
                                              StringBuilder thinkingBuffer) {
        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    parseAndAppendToBuffers(chunk, finalAnswerBuffer, thinkingBuffer);
                })
                .doOnCancel(() -> handleCancel(sink, finished))
                .doFinally(signalType -> handleFinally(signalType, conversationId, finalAnswerBuffer,
                        thinkingBuffer, finished));
    }

    /**
     * 解析消息并追加到对应的缓冲区
     */
    private void parseAndAppendToBuffers(String chunk, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        try {
            JSONObject json = JSON.parseObject(chunk);
            String type = json.getString("type");
            if ("text".equals(type)) {
                finalAnswerBuffer.append(json.getString("content"));
            } else if ("thinking".equals(type)) {
                thinkingBuffer.append(json.getString("content"));
            }
        } catch (Exception e) {
            // 解析失败，默认为 text 类型
            finalAnswerBuffer.append(chunk);
        }
    }

    /**
     * 处理取消操作
     */
    private void handleCancel(Sinks.Many<String> sink, AtomicBoolean finished) {
        finished.set(true);
        taskManager.stopTask(currentConversationId);
    }

    /**
     * 处理流结??
     */
    private void handleFinally(reactor.core.publisher.SignalType signalType, String conversationId,
                               StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer,
                               AtomicBoolean finished) {
        log.info("流结束，类型: {}, 最终答案长度 {}, 思考过程长度 {}",
                signalType, finalAnswerBuffer.length(), thinkingBuffer.length());

        // 保存结果到会??
        saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer);

        // 正常结束只移除任务状态，用户点击停止才发送停止消??
        taskManager.completeTask(conversationId);

        // 清理资源
        cleanupResources(finished);
    }

    /**
     * 清理资源
     */
    private void cleanupResources(AtomicBoolean finished) {
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
        if (finished != null) {
            finished.set(true);
        }
    }

    /**
     * 需求澄清阶段。
     */
    private void clarifyRequirementPhase(OverAllState state, Sinks.Many<String> sink,
                                         AtomicBoolean finished, StringBuilder thinkingBuffer, Runnable onComplete) {
        emit(sink, finished, "\n正在分析您的需求...\n", "thinking", thinkingBuffer);

        List<Message> messages = new ArrayList<>();
        // 先注入时间信息。
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.REQUIREMENT_CLARIFICATION));
        messages.addAll(state.getMessages());

        StringBuilder responseBuffer = new StringBuilder();
        final boolean[] inThinkHolder = {false};
        StreamingTextDelta textDelta = new StreamingTextDelta();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    String delta = textDelta.apply(chunk);
                    if (delta.isEmpty()) {
                        return;
                    }
                    ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(delta, inThinkHolder[0]);
                    inThinkHolder[0] = parseResult.inThink();
                    for (ThinkTagParser.Segment segment : parseResult.segments()) {
                        emit(sink, finished, segment.content(), "thinking", thinkingBuffer);
                        if (!segment.thinking()) {
                            responseBuffer.append(segment.content());
                        }
                    }
                })
                .doOnComplete(() -> handleClarificationComplete(responseBuffer, sink, finished,
                        thinkingBuffer, onComplete))
                .doOnError(err -> handleError("需求澄清异常", err, sink, finished))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        compositeDisposable.add(disposable);
    }

    /**
     * 处理需求澄清完成。
     */
    private void handleClarificationComplete(StringBuilder responseBuffer, Sinks.Many<String> sink,
                                             AtomicBoolean finished, StringBuilder thinkingBuffer, Runnable onComplete) {
        String response = responseBuffer.toString();
        emit(sink, finished, "\n需求分析完成\n", "thinking", thinkingBuffer);

        boolean needsMoreInfo = response.contains("【需要补充信息】");

        if (needsMoreInfo) {
            // 需要补充信息，停止流程
            String pauseMessage = "⏸【暂停深入研究】" + response.replace("【需要补充信息】", "").trim();
            sink.tryEmitNext(createTextResponse(pauseMessage));
            complete(sink, finished);
        } else {
            // 信息充足，继续下一步。
            emit(sink, finished, "信息充足，准备生成研究主题\n", "thinking", thinkingBuffer);
            onComplete.run();
        }
    }

    /**
     * 研究主题生成阶段
     */
    private void generateResearchTopicPhase(OverAllState state, Sinks.Many<String> sink,
                                            AtomicBoolean finished, StringBuilder thinkingBuffer, Runnable onComplete) {
        // 任务推理分析（新增）
        analyzeTaskWithReasoning(state, sink, finished, thinkingBuffer);
        
        emit(sink, finished, "📝 正在生成研究主题...\n", "thinking", thinkingBuffer);

        List<Message> messages = new ArrayList<>();
        // 先注入时间信息。
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.RESEARCH_TOPIC_GENERATION));

        // 添加历史消息和对话上下文
        if (CollectionUtils.isNotEmpty(state.getMessages())) {
            messages.addAll(state.getMessages());
        }

        // 添加用户原始问题
        messages.add(new UserMessage("<original_question>" + state.getQuestion() + "</original_question>"));

        StringBuilder topicBuffer = new StringBuilder();
        final boolean[] topicInThinkHolder = {false};
        StreamingTextDelta textDelta = new StreamingTextDelta();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    String delta = textDelta.apply(chunk);
                    if (delta.isEmpty()) {
                        return;
                    }
                    ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(delta, topicInThinkHolder[0]);
                    topicInThinkHolder[0] = parseResult.inThink();
                    for (ThinkTagParser.Segment segment : parseResult.segments()) {
                        emit(sink, finished, segment.content(), "thinking", thinkingBuffer);
                        if (!segment.thinking()) {
                            topicBuffer.append(segment.content());
                        }
                    }
                })
                .doOnComplete(() -> handleResearchTopicComplete(topicBuffer, state, sink, finished,
                        thinkingBuffer, onComplete))
                .doOnError(err -> handleError("研究主题生成异常", err, sink, finished))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        compositeDisposable.add(disposable);
    }

    /**
     * 任务推理分析（类似 OpenAI o1 的推理过程展示）。
     */
    private void analyzeTaskWithReasoning(OverAllState state, Sinks.Many<String> sink,
                                         AtomicBoolean finished, StringBuilder thinkingBuffer) {
        try {
            com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService reasoningService = 
                new com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService();
            
            emit(sink, finished, "🤔 正在分析任务...\n", "reasoning", thinkingBuffer);
            
            var analysisResult = reasoningService.analyzeTask(state.getQuestion());
            
            String reasoningOutput = String.format("""
                    
                    任务分析结果：
                    任务类型：%s
                    预估步骤：%d 步
                    难度评估：%s
                    信息源：%s
                    
                    """,
                    analysisResult.getTaskType(),
                    analysisResult.getEstimatedSteps(),
                    analysisResult.getDifficulty(),
                    analysisResult.needsMultipleSources() ? "需要多个信息源" : "单一信息源即可");
            
            emit(sink, finished, reasoningOutput, "reasoning", thinkingBuffer);
            
        } catch (Exception e) {
            log.warn("[PlanExecuteAgent] 任务分析失败: {}", e.getMessage());
            // 分析失败不影响主流程，继续执行。
        }
    }

    /**
     * 处理研究主题生成完成
     */
    private void handleResearchTopicComplete(StringBuilder topicBuffer, OverAllState state,
                                             Sinks.Many<String> sink, AtomicBoolean finished,
                                             StringBuilder thinkingBuffer, Runnable onComplete) {
        String topic = topicBuffer.toString();
        state.setRefinedResearchTopic(topic);

        emit(sink, finished, "\n研究主题已生成\n\n", "thinking", thinkingBuffer);
        onComplete.run();
    }

    /**
     * 执行循环阶段
     */
    private void executeLoopPhase(OverAllState state, Sinks.Many<String> sink,
                                  AtomicBoolean finished, StringBuilder finalAnswerBuffer,
                                  StringBuilder thinkingBuffer) {
        Mono<Void> executionMono = executeLoop(state, sink, finished, finalAnswerBuffer,
                thinkingBuffer);

        Disposable executionDisposable = executionMono.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {
                        },
                        e -> handleExecutionError(e, sink, finished)
                );

        compositeDisposable.add(executionDisposable);
    }

    /**
     * 处理执行过程中的异常
     */
    private void handleExecutionError(Throwable e, Sinks.Many<String> sink, AtomicBoolean finished) {
        // 检查是否是中断导致的异常。
        if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
            log.info("PlanExecuteAgent 执行被用户停止 {}", e.getMessage());
        } else {
            log.error("PlanExecuteAgent execute error", e);
            error(sink, finished, e);
        }
    }

    /**
     * 处理错误
     */
    private void handleError(String logMessage, Throwable err, Sinks.Many<String> sink, AtomicBoolean finished) {
        log.error(logMessage, err);
        error(sink, finished, err);
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        if (sessionService == null || currentSessionId == null) {
            return;
        }

        try {
            // 检查是否有任何内容需要保存。
            boolean hasContent = finalAnswerBuffer.length() > 0 || thinkingBuffer.length() > 0;

            if (!hasContent) {
                log.info("没有内容需要保存 conversationId={}", conversationId);
                return;
            }

            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String referenceJson = "";
            if (allReferences != null && !allReferences.isEmpty()) {
                referenceJson = createReferenceResponse(JSON.toJSONString(allReferences));
            }
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .reference(referenceJson)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}, conversationId={}", currentSessionId, conversationId);
        } catch (Exception e) {
            log.error("保存结果到会话失败", e);
        }
    }

    /**
     * 发送响应（缓冲区收集由 wrapSinkWithHandlers 统一处理）。
     */
    private void emit(Sinks.Many<String> sink,
                      AtomicBoolean finished,
                      String content,
                      String type) {

        if (finished.get()) {
            return;
        }
        sink.tryEmitNext(createResponse(content, type));
    }

    /**
     * 发送响应。
     */
    private void emit(Sinks.Many<String> sink,
                      AtomicBoolean finished,
                      String content,
                      String type,
                      StringBuilder thinkingBuffer) {

        if (finished.get()) {
            return;
        }
        sink.tryEmitNext(createResponse(content, type));
    }

    private void complete(Sinks.Many<String> sink,
                          AtomicBoolean finished) {

        if (finished.compareAndSet(false, true)) {
            sink.tryEmitComplete();
        }
    }

    private void error(Sinks.Many<String> sink,
                       AtomicBoolean finished,
                       Throwable e) {

        if (finished.compareAndSet(false, true)) {
            sink.tryEmitError(e);
        }
    }

    private Mono<Void> executeLoop(OverAllState state,
                                   Sinks.Many<String> sink,
                                   AtomicBoolean finished,
                                   StringBuilder finalAnswerBuffer,
                                   StringBuilder thinkingBuffer) {

        return Mono.fromRunnable(() -> {
            try {
                while (state.getRound() < maxRounds && !finished.get() && !compositeDisposable.isDisposed()) {

                    state.nextRound();
                    log.info("===== Plan-Execute Round {} =====", state.getRound());

                    // 输出轮次分隔。
                    emit(sink, finished, "\n第 " + state.getRound() + " 轮研究开始\n", "thinking", thinkingBuffer);

                    List<PlanTask> plan = generatePlan(state, sink, finished, thinkingBuffer);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }

                    if (plan.isEmpty() || plan.stream().allMatch(t -> t.id() == null)) {
                        break;
                    }

                    // 执行计划前的分隔
                    emit(sink, finished, "\n--- 开始执行任务 ---\n\n", "thinking", thinkingBuffer);

                    Map<String, TaskResult> results = executePlan(plan, state, sink, finished, thinkingBuffer);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }

                    // 执行完成后的分隔
                    emit(sink, finished, "\n--- 任务执行完成 ---\n\n", "thinking", thinkingBuffer);

                    CritiqueResult critique = critique(state, plan, results, sink, finished, thinkingBuffer);
                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }

                    if (critique.passed()) {
                        break;
                    }

                    state.add(new AssistantMessage("""
                            【Critique Feedback】
                            %s
                            """.formatted(critique.feedback())));

                    // 下一轮前的分隔。
                    emit(sink, finished, "\n--- 准备进入下一轮迭代 ---\n", "thinking", thinkingBuffer);

                    compressIfNeeded(state, sink, finished, thinkingBuffer);
                }

                // 所有轮次完成后的分隔。
                emit(sink, finished, "\n研究阶段完成，准备生成最终报告\n", "thinking", thinkingBuffer);

                summarizeStream(state, sink, finished, thinkingBuffer);
            } catch (Exception e) {
                // 检查是否是 dispose 导致的异常。
                if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                        || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                    log.info("PlanExecuteAgent 执行被用户停止 {}", e.getMessage());
                    // 发送停止消息。
                    sink.tryEmitNext("{\"type\":\"text\",\"content\":\"用户已停止生成\\n\"}");
                    complete(sink, finished);
                } else {
                    log.error("PlanExecuteAgent 执行异常", e);
                    throw e;
                }
            }
        });
    }

    private List<PlanTask> generatePlan(OverAllState state, Sinks.Many<String> sink, AtomicBoolean hasSentFinal, StringBuilder thinkingBuffer) {
        String toolDesc = renderToolDescriptions();
        BeanOutputConverter<List<PlanTask>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.PLAN + """
                                                ## 当前上下文
                                                当前轮次: %s

                                                ## 可用工具说明（仅用于规划参考）
                                                %s

                                                ## 输出格式
                                                %s
                        """.formatted(state.getRound(), toolDesc, converter.getFormat())),
                new UserMessage("""
                        【研究主题】
                        %s

                        【对话历史】
                        %s

                        ## 重要约束
                        如果会话历史中存在【Critique Feedback】，你必须：
                        1. 仔细分析反馈中指出的不足
                        2. 新的计划必须直接解决这些问题
                        3. 不要重复之前失败的尝试
                        """.formatted(
                        state.getRefinedResearchTopic() != null ? state.getRefinedResearchTopic() : state.getQuestion(),
                        state.renderFullContext()
                ))
        ));

        // 只输出状态，不流式输出计划内容。
        emit(sink, hasSentFinal, "📋 正在生成执行计划...\n", "thinking", thinkingBuffer);

        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return new ArrayList<>();
        }

        String json = chatClient.prompt()
                .messages(prompt.getInstructions())
                .call()
                .content();

        List<PlanTask> planTasks = converter.convert(ThinkTagParser.stripThinkTags(json));

        emit(sink, hasSentFinal, "\n执行计划已生成，共 " + planTasks.size() + " 个任务\n", "thinking", thinkingBuffer);
        emitPlanUpdate(sink, hasSentFinal, state, planTasks);

        // 将执行计划表格式化为纯文本展示。
        if (!planTasks.isEmpty()) {
            StringBuilder planText = new StringBuilder("\n📋 执行计划表：\n");
            for (PlanTask task : planTasks) {
                planText.append(String.format("  🟠 %s \n", task.instruction()));
            }
            emit(sink, hasSentFinal, planText.toString(), "thinking", thinkingBuffer);
        }
        return planTasks;
    }

    private void emitPlanUpdate(Sinks.Many<String> sink,
                                AtomicBoolean finished,
                                OverAllState state,
                                List<PlanTask> planTasks) {
        if (finished.get() || CollectionUtils.isEmpty(planTasks)) {
            return;
        }
        sink.tryEmitNext(createPlanUpdateEvent(state.getRound(), planTasks));
    }

    static String createPlanUpdateEvent(int round, List<PlanTask> planTasks) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", round > 1 ? "replan" : "plan_update");
        payload.put("title", round > 1 ? "第 " + round + " 轮补充执行计划" : "深度研究执行计划");
        payload.put("reason", round > 1 ? "根据上一轮评估反馈调整剩余任务" : "模型已生成可执行计划");
        payload.put("structuredSteps", (planTasks == null ? List.<PlanTask>of() : planTasks).stream()
                .filter(Objects::nonNull)
                .filter(task -> StringUtils.hasText(task.instruction()))
                .map(PlanExecuteAgent::planTaskPayload)
                .toList());
        return JSON.toJSONString(payload);
    }

    static String createReflectionEvent(int round, CritiqueResult critique) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "reflection");
        payload.put("round", Math.max(1, round));
        payload.put("passed", critique != null && critique.passed());
        payload.put("feedback", critique == null ? "" : Objects.toString(critique.feedback(), ""));
        payload.put("action", critique != null && critique.passed() ? "summarize" : "replan");
        return JSON.toJSONString(payload);
    }

    private static Map<String, Object> planTaskPayload(PlanTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stepId", StringUtils.hasText(task.id()) ? task.id() : "S" + Math.max(1, task.order()));
        data.put("instruction", task.instruction());
        data.put("order", Math.max(1, task.order()));
        data.put("assignedAgent", "executor");
        data.put("dependencies", List.of());
        return data;
    }

    private Map<String, TaskResult> executePlan(List<PlanTask> plan, OverAllState state, Sinks.Many<String> sink,
                                                AtomicBoolean hasSentFinal, StringBuilder thinkingBuffer) {

        Map<String, TaskResult> results = new ConcurrentHashMap<>();

        // ??order 分组：order 相同??task 可并??
        Map<Integer, List<PlanTask>> grouped = plan.stream().collect(Collectors.groupingBy(PlanTask::order));

        Map<String, String> accumulatedResults = new ConcurrentHashMap<>();

        // ??order 顺序执行（不??order 串行??
        for (Integer order : new TreeSet<>(grouped.keySet())) {
            if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
                break;
            }

            // 构建任务执行的依赖上下文（只传递上一??order 的结果）
            String dependencyContext = buildDependencyContext(accumulatedResults, plan, order);

            List<PlanTask> tasks = grouped.get(order);

            // 使用CountDownLatch等待当前order组全部完??
            CountDownLatch latch = new CountDownLatch(tasks.size());

            for (PlanTask task : tasks) {
                // 使用Mono包装任务执行
                Disposable taskDisposable = Mono.fromRunnable(() -> {
                            boolean acquired = false;
                            try {
                                // 检查是否已被停??
                                if (compositeDisposable.isDisposed()) {
                                    return;
                                }

                                // 获取执行许可
                                toolSemaphore.acquire();
                                acquired = true;

                                if (task == null || task.id() == null || task.id().isEmpty()) {
                                    return;
                                }

                                // 再次检查，避免在acquire后被停止
                                if (compositeDisposable.isDisposed()) {
                                    return;
                                }

                                TaskResult result = executeWithRetry(task, dependencyContext, sink, hasSentFinal, thinkingBuffer);
                                results.put(task.id(), result);

                                if (result.success() && result.output() != null) {
                                    accumulatedResults.put(task.id(), result.output());
                                }

                                // 构建任务结果消息，只在有错误时才显示 error
                                StringBuilder resultMessage = new StringBuilder();
                                resultMessage.append("【Completed Task Result】\n");
                                resultMessage.append("taskId: ").append(task.id()).append("\n");
                                resultMessage.append("success: ").append(result.success()).append("\n");
                                if (result.output() != null) {
                                    resultMessage.append("result:\n").append(result.output()).append("\n");
                                }
                                if (result.error() != null) {
                                    resultMessage.append("error:\n").append(result.error()).append("\n");
                                }
                                resultMessage.append("【End Task Result】");

                                state.add(new AssistantMessage(resultMessage.toString()));

                            } catch (InterruptedException e) {
                                log.info("Task {} 执行被中断", task.id());
                                Thread.currentThread().interrupt();

                                results.put(task.id(),
                                        new TaskResult(
                                                task.id(),
                                                false,
                                                null,
                                                "Task execution interrupted"
                                        ));
                            } catch (Exception e) {
                                // 检查是否是中断导致的异??
                                if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                                        || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                    log.info("Task {} 执行被用户停止 {}", task.id(), e.getMessage());
                                    results.put(task.id(),
                                            new TaskResult(
                                                    task.id(),
                                                    false,
                                                    null,
                                                    "Task execution interrupted by user"
                                            ));
                                } else {
                                    log.error("Task execution error", e);
                                    results.put(task.id(),
                                            new TaskResult(
                                                    task.id(),
                                                    false,
                                                    null,
                                                    "Task execution error: " + e.getMessage()
                                            ));
                                }
                            } finally {
                                // 释放许可
                                if (acquired) {
                                    toolSemaphore.release();
                                }
                                latch.countDown();
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();

                // 将任务的disposable添加到composite
                compositeDisposable.add(taskDisposable);
            }

            // 等待当前order组全部完??
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("executePlan interrupted");
                break;
            }
        }

        return results;
    }


    /**
     * 执行单个任务（带上下文）
     * 上下文格式：【Available Results】\n[依赖结果]\n\n【Current Task】\n[任务指令]
     *
     * @param task              要执行的任务
     * @param dependencyContext 依赖上下文（只包含依赖结果）
     * @param sink              响应??
     * @param hasSentFinal      是否已发送最终结??
     * @param thinkingBuffer    思考过程缓??
     * @return 任务执行结果
     */
    private TaskResult executeWithRetry(PlanTask task, String dependencyContext,
                                        Sinks.Many<String> sink, AtomicBoolean hasSentFinal, StringBuilder thinkingBuffer) {

        Throwable lastError = null;
        long startedAt = System.currentTimeMillis();
        String toolCallId = deepResearchToolCallId(task);
        emit(sink, hasSentFinal, "⚙️ 正在执行任务 " + task.id() + " : " + task.instruction() + "\n", "thinking", thinkingBuffer);
        emitJson(sink, hasSentFinal, createDeepResearchToolStartEvent(toolCallId, task, dependencyContext));

        // 检查是否已被停??
        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            emitJson(sink, hasSentFinal, createDeepResearchToolEndEvent(
                    toolCallId, task, false, null, "任务被用户停止", List.of(), startedAt));
            return new TaskResult(task.id(), false, null, "任务被用户停止");
        }
        try {
            // 构建完整任务上下文（依赖 + 当前任务指令??
            String fullContext = """
                                【Available Results】
                                %s
                                
                                【Current Task】
                                %s
                    """.formatted(
                    dependencyContext,
                    task.instruction()
            );

            SimpleReactAgent agent = SimpleReactAgent.builder()
                    .chatModel(chatModel)
                    .tools(tools)
                    .maxRounds(5)
                    .systemPrompt(joinPrompts(PlanExecutePrompts.EXECUTE, systemPrompt))
                    .build();

            SimpleReactResult result = agent.callWithReference(null, fullContext);

            if (compositeDisposable.isDisposed()) {
                emitJson(sink, hasSentFinal, createDeepResearchToolEndEvent(
                        toolCallId, task, false, null, "任务被用户停止", result.getSearchResults(), startedAt));
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }

            // 收集搜索结果??allReferences
            if (result.getSearchResults() != null && !result.getSearchResults().isEmpty()) {
                synchronized (allReferences) {
                    allReferences.addAll(result.getSearchResults());
                }
            }

            String answer = result.getAnswer();
            emit(sink, hasSentFinal, "执行结果: " + answer + "\n\n", "thinking", thinkingBuffer);
            emitJson(sink, hasSentFinal, createDeepResearchToolEndEvent(
                    toolCallId, task, true, answer, null, result.getSearchResults(), startedAt));
            return new TaskResult(task.id(), true, answer, null);
        } catch (Exception e) {
            // 检查是否是中断导致的异??
            if (compositeDisposable.isDisposed() || Thread.currentThread().isInterrupted()
                    || (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                log.info("Task {} 执行被用户停止 {}", task.id(), e.getMessage());
                emitJson(sink, hasSentFinal, createDeepResearchToolEndEvent(
                        toolCallId, task, false, null, "任务被用户停止", List.of(), startedAt));
                return new TaskResult(task.id(), false, null, "任务被用户停止");
            }
            lastError = e;
            log.warn("Task {} failed: {}", task.id(), e.getMessage());
        }

        // 执行失败
        emit(sink, hasSentFinal, "\n任务 " + task.id() + " 执行失败: " + (lastError == null ? "unknown error" : lastError.getMessage()) + "\n\n", "thinking", thinkingBuffer);
        emitJson(sink, hasSentFinal, createDeepResearchToolEndEvent(
                toolCallId, task, false, null,
                lastError == null ? "unknown error" : lastError.getMessage(), List.of(), startedAt));
        return new TaskResult(
                task.id(),
                false,
                null,
                lastError == null ? "unknown error" : lastError.getMessage()
        );
    }

    private void emitJson(Sinks.Many<String> sink, AtomicBoolean finished, String json) {
        if (finished.get() || !StringUtils.hasText(json)) {
            return;
        }
        sink.tryEmitNext(json);
    }

    private static String deepResearchToolCallId(PlanTask task) {
        String taskId = task == null ? "unknown" : Objects.toString(task.id(), "unknown");
        return "deep_research_step_" + taskId.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.nanoTime();
    }

    static String createDeepResearchToolStartEvent(String toolCallId, PlanTask task, String dependencyContext) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("taskId", task == null ? "" : Objects.toString(task.id(), ""));
        arguments.put("instruction", task == null ? "" : Objects.toString(task.instruction(), ""));
        arguments.put("order", task == null ? 1 : Math.max(1, task.order()));
        arguments.put("dependencyCharCount", dependencyContext == null ? 0 : dependencyContext.length());
        arguments.put("dependencyContext", limitText(dependencyContext, 1200));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_start");
        payload.put("toolName", "deep_research_step");
        payload.put("toolCallId", toolCallId);
        payload.put("action", "execute_step");
        payload.put("arguments", arguments);
        return JSON.toJSONString(payload);
    }

    static String createDeepResearchToolEndEvent(String toolCallId,
                                                 PlanTask task,
                                                 boolean success,
                                                 String answer,
                                                 String error,
                                                 List<SearchResult> references,
                                                 long startedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolName", "deep_research_step");
        result.put("taskId", task == null ? "" : Objects.toString(task.id(), ""));
        result.put("success", success);
        result.put("summary", success ? limitText(answer, 360) : Objects.toString(error, ""));
        result.put("content", Objects.toString(answer, ""));
        result.put("references", referencePayload(references));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_end");
        payload.put("toolName", "deep_research_step");
        payload.put("toolCallId", toolCallId);
        payload.put("status", success ? "success" : "failed");
        payload.put("latencyMillis", Math.max(0L, System.currentTimeMillis() - startedAt));
        payload.put("result", result);
        if (!success) {
            payload.put("error", Objects.toString(error, ""));
        }
        return JSON.toJSONString(payload);
    }

    private static List<Map<String, Object>> referencePayload(List<SearchResult> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream()
                .filter(Objects::nonNull)
                .map(reference -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("url", Objects.toString(reference.url(), ""));
                    data.put("title", Objects.toString(reference.title(), ""));
                    data.put("content", Objects.toString(reference.content(), ""));
                    return data;
                })
                .toList();
    }

    private static String limitText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    /**
     * 构建任务执行的依赖上下文
     * 规则：同 order 的任务不传依赖（并行），不同 order 的任务只传递上一??order 的结??
     * 注意：此方法只返回【Available Results】部分，【Current Task】由 executeWithRetry 拼接
     *
     * @param results      所有已完成任务的结??
     * @param plan         当前轮次的执行计划（用于获取任务 order??
     * @param currentOrder 当前任务??order
     * @return 依赖上下文字符串
     */
    private String buildDependencyContext(Map<String, String> results, List<PlanTask> plan, int currentOrder) {
        StringBuilder context = new StringBuilder();

        // 1. 第一??order 的任务没有依??
        if (currentOrder == 1) {
            return context.append("无\n").toString();
        }

        // 2. 收集上一??order 的任务结??
        boolean hasDependencies = false;

        for (Map.Entry<String, String> entry : results.entrySet()) {
            // 查找任务对应??order
            PlanTask task = plan.stream()
                    .filter(t -> t.id() != null && t.id().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);

            if (task != null && task.order() == currentOrder - 1) {
                // 只有上一??order 的结果才是依??
                if (!hasDependencies) {
                    context.append("任务 ");
                    hasDependencies = true;
                }
                context.append(String.format("%s: %s\n\n",
                        entry.getKey(),
                        entry.getValue()));
            }
        }

        if (!hasDependencies) {
            context.append("无\n");
        }

        return context.toString();
    }


    /**
     * 批判当前轮次的研究结??
     * 上下文：用户问题 + 研究主题 + 当前轮次的执行计??+ 当前轮次的工具结??
     *
     * @param state          整体状??
     * @param currentPlan    当前轮次的执行计??
     * @param currentResults 当前轮次的任务执行结??
     * @param sink           响应??
     * @param hasSentFinal   是否已发送最终结??
     * @param thinkingBuffer 思考过程缓??
     * @return 批判结果
     */
    private CritiqueResult critique(OverAllState state, List<PlanTask> currentPlan,
                                    Map<String, TaskResult> currentResults,
                                    Sinks.Many<String> sink, AtomicBoolean hasSentFinal,
                                    StringBuilder thinkingBuffer) {
        BeanOutputConverter<CritiqueResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        emit(sink, hasSentFinal, "\n🔍 正在评估当前研究结果...\n", "thinking", thinkingBuffer);

        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return new CritiqueResult(true, "任务已取消");
        }

        // 构建批判的用户消息（只包含当前轮次的上下文）
        StringBuilder userMessage = new StringBuilder();
        userMessage.append("【用户原始问题】\n");
        userMessage.append(state.getQuestion());

        userMessage.append("\n\n【研究主题】\n");
        userMessage.append(state.getRefinedResearchTopic() != null ?
                state.getRefinedResearchTopic() : "未生成研究主题");

        userMessage.append("\n\n【当前轮次的执行计划】\n");
        if (currentPlan != null && !currentPlan.isEmpty()) {
            for (PlanTask task : currentPlan) {
                userMessage.append(String.format("- %s\n", task.instruction()));
            }
        } else {
            userMessage.append("无\n");
        }

        userMessage.append("\n\n【当前轮次的工具结果】\n");
        if (currentResults != null && !currentResults.isEmpty()) {
            for (Map.Entry<String, TaskResult> entry : currentResults.entrySet()) {
                TaskResult result = entry.getValue();
                if (result != null && result.success() && result.output() != null) {
                    userMessage.append(String.format("任务 %s: %s\n\n",
                            entry.getKey(), result.output()));
                } else if (result != null && !result.success() && result.error() != null) {
                    userMessage.append(String.format("任务 %s: 执行失败 - %s\n\n",
                            entry.getKey(), result.error()));
                }
            }
        } else {
            userMessage.append("无\n");
        }

        String prom = PlanExecutePrompts.CRITIQUE + "\n" + converter.getFormat();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + prom),
                new UserMessage(userMessage.toString())
        ));

        String raw = chatClient.prompt(prompt).call().content();

        CritiqueResult result = converter.convert(ThinkTagParser.stripThinkTags(raw));
        sink.tryEmitNext(createReflectionEvent(state.getRound(), result));

        if (result.passed()) {
            emit(sink, hasSentFinal, "\n研究结果评估通过，准备生成最终报告\n", "thinking", thinkingBuffer);
        } else {
            emit(sink, hasSentFinal, "\n⚠️ 研究结果评估未通过，原因分析：" + result.feedback() + "\n", "thinking", thinkingBuffer);
        }

        return result;
    }

    private void compressIfNeeded(OverAllState state, Sinks.Many<String> sink, AtomicBoolean hasSentFinal, StringBuilder thinkingBuffer) {
        if (state.currentChars() < contextCharLimit) {
            return;
        }

        log.warn("===== Context too large, compressing ,size is {} =====", state.currentChars());

        emit(sink, hasSentFinal, "📦 上下文过长，正在压缩...\n", "thinking", thinkingBuffer);

        if (hasSentFinal.get() || compositeDisposable.isDisposed()) {
            return;
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + """
                        ## 最大压缩限制（必须遵守）
                        - 你输出的最终内容【总字符数（包含所有标签、空格、换行）】
                        不得超过 %s
                                - 这是硬性上限，不是建议
                                - 如超过该限制，视为压缩失败

                        """.formatted(contextCharLimit) + PlanExecutePrompts.COMPRESS),

                new UserMessage(state.renderFullContext())
        ));

        String snapshot = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        state.clearMessages();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compress has completed, size is {} =====", state.currentChars());

        emit(sink, hasSentFinal, "上下文压缩完成\n", "thinking", thinkingBuffer);
    }


    private void summarizeStream(OverAllState state,
                                 Sinks.Many<String> sink,
                                 AtomicBoolean finished,
                                 StringBuilder thinkingBuffer) {

        emit(sink, finished, "\n正在生成最终研究报告...\n\n", "thinking", thinkingBuffer);

        final boolean[] summarizeInThinkHolder = {false};
        StreamingTextDelta textDelta = new StreamingTextDelta();

        // 提取工具执行结果，排除中间过程。
        String toolResults = state.extractToolResults();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.SUMMARIZE),
                new UserMessage("""
                                        【用户原始问题】
                                        %s

                                        【研究主题】
                                        %s

                                        【工具检索结果】
                                        %s
                        """.formatted(
                        state.getQuestion(),
                        state.getRefinedResearchTopic() != null ? state.getRefinedResearchTopic() : "未生成研究主题",
                        toolResults.isEmpty() ? "（未检索到相关结果）" : toolResults
                ))
        ));

        Disposable disposable = chatClient.prompt()
                .messages(prompt.getInstructions())
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {

                    if (finished.get() || compositeDisposable.isDisposed()) {
                        return;
                    }

                    if (chunk == null
                            || chunk.getResult() == null
                            || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult().getOutput().getText();
                    if (text == null) {
                        return;
                    }

                    String delta = textDelta.apply(text);
                    if (delta.isEmpty()) {
                        return;
                    }

                    ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(delta, summarizeInThinkHolder[0]);
                    summarizeInThinkHolder[0] = parseResult.inThink();
                    for (ThinkTagParser.Segment segment : parseResult.segments()) {
                        if (segment.thinking()) {
                            emit(sink, finished, segment.content(), "thinking", thinkingBuffer);
                        } else {
                            emit(sink, finished, segment.content(), "text");
                        }
                    }
                })
                .doOnComplete(() -> {
                    // ??text 输出后，输出参考来??
                    if (!allReferences.isEmpty()) {
                        sink.tryEmitNext(createReferenceResponse(JSON.toJSONString(allReferences)));
                    }

                    complete(sink, finished);
                })
                .doOnError(e -> error(sink, finished, e))
                .subscribe();

        // 将summarizeStream的disposable添加到composite
        compositeDisposable.add(disposable);
    }

    private String renderToolDescriptions() {
        if (tools == null || tools.isEmpty()) {
            return "（当前无可用工具）";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : tools) {
            sb.append("- ")
                    .append(tool.getToolDefinition().name())
                    .append(": ")
                    .append(tool.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
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
}















