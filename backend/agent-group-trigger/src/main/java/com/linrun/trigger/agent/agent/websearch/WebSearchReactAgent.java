package com.linrun.trigger.agent.agent.websearch;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.entity.record.AgentState;
import com.linrun.trigger.agent.entity.record.RoundMode;
import com.linrun.trigger.agent.entity.record.RoundState;
import com.linrun.trigger.agent.utils.ThinkTagParser;
import com.linrun.trigger.agent.entity.record.SearchResult;
import com.linrun.trigger.agent.prompts.ReactAgentPrompts;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.trigger.agent.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSearch React Agent
 * 基于联网搜索的智能问答Agent
 */
@Slf4j
public class WebSearchReactAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private final List<Advisor> advisors;
    private final int maxReflectionRounds;

    private static final Pattern PSEUDO_SEARCH_CALL_PATTERN =
            Pattern.compile("(?is)ToolCall\\s*[:：]\\s*search\\s*\\((.*?)\\)");
    private static final Pattern PSEUDO_SEARCH_QUERY_PATTERN =
            Pattern.compile("(?is)query\\s*=\\s*(?:([\"'])(.*?)\\1|([^,)\\r\\n]+))");
    private static final Pattern PSEUDO_SEARCH_LIMIT_PATTERN =
            Pattern.compile("(?is)(?:max_results|maxResults|limit)\\s*=\\s*(\\d+)");
    private static final Pattern PSEUDO_SEARCH_START_PATTERN =
            Pattern.compile("(?is)(ToolCall\\s*[:：]|(?:正在|即将|将为|为你|需要|调用|使用|实时).{0,40}(?:搜索|检索|search))");

    public WebSearchReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, String systemPrompt, int maxRounds,
                               ChatMemory chatMemory, List<Advisor> advisors, int maxReflectionRounds,
                               AiSessionService sessionService, AgentTaskManager taskManager) {
        super(name, chatModel, "websearch");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.advisors = advisors;
        this.maxReflectionRounds = maxReflectionRounds;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集合（并发安全：多个工具调用在 boundedElastic 线程并行写）
        this.usedTools = ConcurrentHashMap.newKeySet();

        initChatClient();

        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    private void initChatClient() {
        try {
            ChatClient.Builder builder = ChatClient.builder(chatModel);
            if (!CollectionUtils.isEmpty(advisors)) {
                builder.defaultAdvisors(advisors);
            }
            if (CollectionUtils.isEmpty(tools)) {
                OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                        .temperature(0.2d)
                        .build();
                this.chatClient = builder.defaultOptions(chatOptions).build();
                return;
            }
            OpenAiChatOptions toolOptions = OpenAiChatOptions.builder()
                    .temperature(0.2d)
                    .parallelToolCalls(false)
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();
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
     * 流式输出
     */
    public Flux<String> stream(String question) {
        return streamInternal(null, question);
    }

    /**
     * 带会话记忆的流式输出
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    private Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // ===== 加载 System Prompt（始终放在最开始）=====
        messages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        currentQuestion = question;

        // 添加记忆并保存到数据库
        if (sessionService != null) {
            // 保存用户问题到数据库
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
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

        // 收集最终答案（纯文本），存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();

        AgentState agentState = new AgentState();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    // 解析 JSON，如果是 type=text，则只拼接 content；如果是 type=thinking，则拼接 thinking
                    try {
                        JsonNode json = JsonUtils.parse(chunk);
                        String type = json != null && json.has("type") ? json.get("type").asText() : null;
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.has("content") ? json.get("content").asText() : "");
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.has("content") ? json.get("content").asText() : "");
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼接
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                })
                .doFinally(signalType -> {
                    log.info("最终答案 {}", finalAnswerBuffer);
                    log.info("思考过程 {}", thinkingBuffer);

                    // 保存结果到会话
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer, agentState);

                    // 流正常结束时只移除任务状态，用户点击停止才发送停止消息
                    if (taskManager != null) {
                        taskManager.completeTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer, AgentState agentState) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String referenceJson = "";
            if (!agentState.searchResults.isEmpty()) {
                referenceJson = createReferenceResponse(JsonUtils.toJson(agentState.searchResults));
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
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink, AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer))
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

        // 一旦发现 tool_call，立即进入 TOOL_CALL 模式
        if (tc != null && !tc.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;

            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 还没出现 tool_call，使用 ThinkTagParser 解析 <think/> 标签
        if (text != null) {
            ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
            state.inThink = parseResult.inThink();
            for (ThinkTagParser.Segment segment : parseResult.segments()) {
                if (segment.thinking()) {
                    sink.tryEmitNext(createThinkingResponse(segment.content()));
                } else {
                    if (handlePseudoToolCallText(segment.content(), state)) {
                        continue;
                    }
                    state.pendingTextBuffer.append(segment.content());
                }
            }
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");

                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // 新的 toolcall
        state.toolCalls.add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state,
                             AtomicLong roundCounter, AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer,
                             boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {

        if (state.pseudoToolCallTextDetected && state.getMode() != RoundMode.TOOL_CALL) {
            AssistantMessage.ToolCall pseudoCall = parsePseudoSearchToolCall(state.rawTextBuffer.toString());
            if (pseudoCall == null) {
                pseudoCall = fallbackSearchToolCall();
            }
            if (pseudoCall != null) {
                state.mode = RoundMode.TOOL_CALL;
                state.toolCalls.add(pseudoCall);
            } else {
                sink.tryEmitNext(createTextResponse("\n\n搜索工具调用格式不完整，请重新提问或换一个更具体的问题"));
                sink.tryEmitComplete();
                hasSentFinalResult.set(true);
                return;
            }
        }

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.getMode() != RoundMode.TOOL_CALL) {
            flushPendingText(state, sink);
            String referenceJson = "";
            String toolsStr = getUsedToolsString();
            String finalText = state.textBuffer.toString();

            // 输出参考链接
            if (!agentState.searchResults.isEmpty()) {
                String reference = JsonUtils.toJson(agentState.searchResults);
                referenceJson = createReferenceResponse(reference);
                sink.tryEmitNext(referenceJson);
            }

            // 输出推荐问题
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    currentRecommendations = recommendations; // 保存用于数据库存储
                    String recommendJson = createRecommendResponse(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }

            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), state.toolCalls);
        messages.add(assistantMsg);

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, state, conversationId, useMemory, agentState, thinkingBuffer);
            return;
        }

        executeToolCalls(sink, state.toolCalls, messages, hasSentFinalResult, state, agentState, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId, agentState, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult, RoundState state,
                                  String conversationId, boolean useMemory, AgentState agentState, StringBuilder thinkingBuffer) {
        // 创建新的消息列表，确保系统提示词在最前面
        List<Message> newMessages = new ArrayList<>();

        // 添加系统提示词
        newMessages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
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

        // 替换原消息列表
        messages.clear();
        messages.addAll(newMessages);

        // 收集最终文本
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
                    String referenceJson = "";
                    String finalText = finalTextBuffer.toString();

                    // 输出参考链接
                    if (!agentState.searchResults.isEmpty()) {
                        String reference = JsonUtils.toJson(agentState.searchResults);
                        referenceJson = createReferenceResponse(reference);
                        sink.tryEmitNext(referenceJson);
                    }

                    // 输出推荐问题
                    if (enableRecommendations) {
                        String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations; // 保存用于数据库存储
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

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls, List<Message> messages, AtomicBoolean hasSentFinalResult, RoundState state, AgentState agentState, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        // 保证顺序一致性
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    // 工具未找到时，也放入 responseMap
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, "{ \"error\": \"工具未找到：" + toolName + "\" }"));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }
                if (toolName.contains("search")) {
                    JsonNode args = JsonUtils.parse(argsJson);
                    String query = args != null && args.has("query") ? args.get("query").asText() : null;
                    // 发送 thinking 消息，表示正在搜索相关信息
                    String queryThink = StringUtils.isNotBlank(query) ? "🔍 正在搜索信息: " + query + "\n" : "🔍 正在搜索相关信息\n";
                    sink.tryEmitNext(createThinkingResponse(queryThink));
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = result.toString();

                    // 记录使用的工具
                    recordUsedTool(toolName);

                    // 解析搜索结果
                    if (toolName.contains("search") || toolName.contains("tavily")) {
                        parseSearchResult(resultStr, agentState);
                    }

                    // 将结果放入 responseMap，key 为 toolCall.id()
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, resultStr));
                } catch (Exception ex) {
                    // 工具执行失败时，也放入 responseMap
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, "{ \"error\": \"工具执行失败：" + ex.getMessage() + "\" }"));
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
            // 按原始 toolCalls 的顺序重组结果
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : originalToolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    // 如果某个工具调用没有响应，添加一个错误响应
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"工具响应丢失\" }"));
                }
            }

            // 一次性添加所有工具响应（按原始顺序）
            messages.add(new ToolResponseMessage(sortedResponses));

            onComplete.run();
        }
    }

    private void parseSearchResult(String resultJson, AgentState state) {
        try {
            JsonNode root = JsonUtils.parse(resultJson);
            JsonNode results = null;

            if (root.isObject() && root.path("results").isArray()) {
                results = root.path("results");
            } else if (root.isArray() && !root.isEmpty()) {
                JsonNode first = root.get(0);
                JsonNode textNode = first.get("text");

                if (textNode == null || textNode.isNull()) {
                    results = root;
                } else {
                    JsonNode textJson;
                    if (textNode.isTextual()) {
                        textJson = JsonUtils.parse(textNode.asText());
                    } else {
                        textJson = textNode;
                    }

                    results = textJson.get("results");
                }
            }

            if (results == null || !results.isArray()) {
                return;
            }

            for (JsonNode item : results) {
                String url = getSafe(item, "url");
                String title = getSafe(item, "title");
                String content = getSafe(item, "content");

                if (url != null && !url.isBlank()) {
                    state.searchResults.add(new SearchResult(url, title, content));
                }
            }

        } catch (Exception e) {
            log.warn("解析搜索结果失败: {}", e.getMessage());
        }
    }

    private boolean handlePseudoToolCallText(String text, RoundState state) {
        if (text == null) {
            return false;
        }
        state.rawTextBuffer.append(text);
        String raw = state.rawTextBuffer.toString();
        boolean looksLikePseudoCall = state.pseudoToolCallTextDetected
                || PSEUDO_SEARCH_START_PATTERN.matcher(raw).find();
        if (!looksLikePseudoCall) {
            return false;
        }

        state.pseudoToolCallTextDetected = true;
        state.pendingTextBuffer.setLength(0);
        AssistantMessage.ToolCall pseudoCall = parsePseudoSearchToolCall(raw);
        if (pseudoCall != null && state.toolCalls.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;
            state.toolCalls.add(pseudoCall);
        }
        return true;
    }

    private AssistantMessage.ToolCall parsePseudoSearchToolCall(String raw) {
        Matcher callMatcher = PSEUDO_SEARCH_CALL_PATTERN.matcher(raw);
        if (!callMatcher.find()) {
            return null;
        }

        String argsText = callMatcher.group(1);
        Matcher queryMatcher = PSEUDO_SEARCH_QUERY_PATTERN.matcher(argsText);
        if (!queryMatcher.find()) {
            return null;
        }
        String query = StringUtils.defaultIfBlank(queryMatcher.group(2), queryMatcher.group(3));
        if (!StringUtils.isNotBlank(query)) {
            return null;
        }

        int maxResults = 5;
        Matcher limitMatcher = PSEUDO_SEARCH_LIMIT_PATTERN.matcher(argsText);
        if (limitMatcher.find()) {
            try {
                maxResults = Math.max(1, Math.min(Integer.parseInt(limitMatcher.group(1)), 5));
            } catch (NumberFormatException ignored) {
                maxResults = 5;
            }
        }

        return buildSearchToolCall(query.trim(), maxResults);
    }

    private AssistantMessage.ToolCall fallbackSearchToolCall() {
        if (!StringUtils.isNotBlank(currentQuestion)) {
            return null;
        }
        return buildSearchToolCall(currentQuestion.trim(), 5);
    }

    private AssistantMessage.ToolCall buildSearchToolCall(String query, int maxResults) {
        ObjectNode arguments = JsonUtils.objectNode();
        arguments.put("query", query);
        arguments.put("maxResults", Math.max(1, Math.min(maxResults, 5)));
        return new AssistantMessage.ToolCall("pseudo-search-" + System.nanoTime(), "function", "search", arguments.toString());
    }

    private void flushPendingText(RoundState state, Sinks.Many<String> sink) {
        if (state.pendingTextBuffer.length() == 0) {
            return;
        }
        String content = state.pendingTextBuffer.toString();
        sink.tryEmitNext(createTextResponse(content));
        state.textBuffer.append(content);
        state.pendingTextBuffer.setLength(0);
    }

    private String getSafe(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
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

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;
        private String systemPrompt = "";
        private int maxReflectionRounds;
        private int maxRounds;
        private List<Advisor> advisors;
        private ChatMemory chatMemory;
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;

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

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder advisors(Advisor... advisors) {
            this.advisors = Arrays.asList(advisors);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxReflectionRounds(int maxReflectionRounds) {
            this.maxReflectionRounds = maxReflectionRounds;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public WebSearchReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空");
            }
            return new WebSearchReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, advisors, maxReflectionRounds, sessionService, taskManager);
        }
    }
}
















