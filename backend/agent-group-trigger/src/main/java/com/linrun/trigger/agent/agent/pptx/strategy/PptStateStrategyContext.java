package com.linrun.trigger.agent.agent.pptx.strategy;

import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.service.*;
import com.linrun.trigger.agent.utils.ImageGenerationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.util.StringUtils;
import com.linrun.trigger.agent.common.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PPT状态策略上下文
 * 用于在策略间共享依赖和工具方法
 */
@Slf4j
public class PptStateStrategyContext {

    /** LLM 末尾输出的结构化决策标记，捕获 JSON 负载。 */
    private static final java.util.regex.Pattern DECISION_MARKER =
            java.util.regex.Pattern.compile("<decision>\\s*(\\{.*?})\\s*</decision>", java.util.regex.Pattern.DOTALL);

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final AiPptInstService pptInstService;
    private final AiPptTemplateService pptTemplateService;
    private final PptPythonRenderService pythonRenderService;
    private final ImageGenerationService imageGenerationService;
    private final MinioService minioService;
    private final AiSessionService sessionService;
    private final AgentTaskManager taskManager;
    private final List<ToolCallback> toolCallbacks;
    private final ChatMemory chatMemory;
    private final String executionMemoryPrompt;

    private Long currentSessionId;
    private String currentConversationId;
    private boolean modifyMode;
    private String modifyQuery;  // 当前修改需求（仅在 modifyMode 为 true 时有效）

    public PptStateStrategyContext(ChatClient chatClient, ChatModel chatModel,
                                    AiPptInstService pptInstService,
                                    AiPptTemplateService pptTemplateService,
                                    PptPythonRenderService pythonRenderService,
                                    ImageGenerationService imageGenerationService,
                                    MinioService minioService,
                                    AiSessionService sessionService,
                                    AgentTaskManager taskManager,
                                    List<ToolCallback> toolCallbacks,
                                    ChatMemory chatMemory) {
        this(chatClient, chatModel, pptInstService, pptTemplateService, pythonRenderService,
                imageGenerationService, minioService, sessionService, taskManager, toolCallbacks,
                chatMemory, "");
    }

    public PptStateStrategyContext(ChatClient chatClient, ChatModel chatModel,
                                    AiPptInstService pptInstService,
                                    AiPptTemplateService pptTemplateService,
                                    PptPythonRenderService pythonRenderService,
                                    ImageGenerationService imageGenerationService,
                                    MinioService minioService,
                                    AiSessionService sessionService,
                                    AgentTaskManager taskManager,
                                    List<ToolCallback> toolCallbacks,
                                    ChatMemory chatMemory,
                                    String executionMemoryPrompt) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.pptInstService = pptInstService;
        this.pptTemplateService = pptTemplateService;
        this.pythonRenderService = pythonRenderService;
        this.imageGenerationService = imageGenerationService;
        this.minioService = minioService;
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.toolCallbacks = toolCallbacks;
        this.chatMemory = chatMemory;
        this.executionMemoryPrompt = executionMemoryPrompt == null ? "" : executionMemoryPrompt;
    }

    // ===== Getters =====

    public ChatClient getChatClient() {
        return chatClient;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public AiPptInstService getPptInstService() {
        return pptInstService;
    }

    public AiPptTemplateService getPptTemplateService() {
        return pptTemplateService;
    }

    public PptPythonRenderService getPythonRenderService() {
        return pythonRenderService;
    }

    public ImageGenerationService getImageGenerationService() {
        return imageGenerationService;
    }

    public MinioService getMinioService() {
        return minioService;
    }

    public AiSessionService getSessionService() {
        return sessionService;
    }

    public AgentTaskManager getTaskManager() {
        return taskManager;
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public String getExecutionMemoryPrompt() {
        return executionMemoryPrompt;
    }

    public void addExecutionMemory(List<Message> messages) {
        if (messages != null && StringUtils.hasText(executionMemoryPrompt)) {
            messages.add(new SystemMessage(executionMemoryPrompt));
        }
    }

    public String enhancePrompt(String prompt) {
        if (!StringUtils.hasText(executionMemoryPrompt)) {
            return prompt;
        }
        return executionMemoryPrompt + "\n\n" + (prompt == null ? "" : prompt);
    }

    public Long getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(Long currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    public String getCurrentConversationId() {
        return currentConversationId;
    }

    public void setCurrentConversationId(String currentConversationId) {
        this.currentConversationId = currentConversationId;
    }

    public void setModifyMode(boolean modifyMode) {
        this.modifyMode = modifyMode;
    }

    public boolean isModifyMode() {
        return modifyMode;
    }

    /**
     * 设置当前修改需求
     */
    public void setModifyQuery(String modifyQuery) {
        this.modifyQuery = modifyQuery;
    }

    /**
     * 获取当前修改需求
     */
    public String getModifyQuery() {
        return modifyQuery;
    }

    /**
     * 保存 Disposable 到任务管理器
     *
     * @param conversationId 会话ID
     * @param disposable    Disposable 对象
     */
    public void setDisposable(String conversationId, reactor.core.Disposable disposable) {
        if (conversationId != null && taskManager != null && disposable != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    /**
     * 加载历史记忆并添加到消息列表
     *
     * @param conversationId 会话ID
     * @param messages      目标消息列表
     * @param skipSystem    是否跳过系统消息
     * @param addLabel     是否添加"对话历史"标签
     */
    public void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (conversationId != null && chatMemory != null) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                if (addLabel) {
                    messages.add(new UserMessage("对话历史"));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    messages.add(msg);
                }
            }
        }
    }

    /**
     * 创建JSON响应
     */
    public String createJsonResponse(String content, String type) {
        return String.format("{\"type\":\"%s\",\"content\":\"%s\"}",
                type, content.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    /**
     * 创建text类型响应
     */
    public String createTextResponse(String content) {
        return createJsonResponse(content, "text");
    }

    /**
     * 创建thinking类型响应
     */
    public String createThinkingResponse(String content) {
        return createJsonResponse(content, "thinking");
    }

    public String createPptStatusResponse(String stage, String message, AiPptInst inst) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ppt_status");
        payload.put("stage", stage == null ? "" : stage);
        payload.put("message", message == null ? "" : message);
        if (inst != null) {
            payload.put("pptInstId", inst.getId() == null ? "" : String.valueOf(inst.getId()));
            payload.put("pptStatus", inst.getStatusEnum() == null ? "" : inst.getStatusEnum().name());
            payload.put("fileUrl", inst.getFileUrl() == null ? "" : inst.getFileUrl());
        }
        return JsonUtils.toJson(payload);
    }

    /**
     * 判断是否可以进入下一步。
     *
     * 弃用中文关键字（如"【开始生成PPT】"）匹配，改为解析 LLM 在回复末尾输出的结构化决策标记
     * <decision>{"decision":"CONTINUE|PAUSE","summary":"..."}</decision>，
     * 用 BeanOutputConverter 反序列化为 PptContinueDecision 枚举判定。
     * 解析失败时安全兜底为暂停，避免误推进。
     */
    public boolean shouldContinueToNextStep(String response) {
        return parseContinueDecision(response).shouldContinue();
    }

    /**
     * 从 LLM 回复中解析结构化决策标记。
     *
     * @param response LLM 完整回复
     * @return 决策结果，解析失败返回 PAUSE
     */
    public PptContinueDecision parseContinueDecision(String response) {
        if (response == null || response.isEmpty()) {
            return new PptContinueDecision(PptContinueDecision.Decision.PAUSE, "");
        }
        String json = extractDecisionJson(response);
        if (json == null || json.isEmpty()) {
            return new PptContinueDecision(PptContinueDecision.Decision.PAUSE, "");
        }
        try {
            BeanOutputConverter<PptContinueDecision> converter =
                    new BeanOutputConverter<>(PptContinueDecision.class);
            PptContinueDecision decision = converter.convert(json);
            if (decision == null) {
                return new PptContinueDecision(PptContinueDecision.Decision.PAUSE, "");
            }
            return decision;
        } catch (Exception e) {
            log.warn("解析 PPT 决策标记失败，兜底暂停: {}", e.getMessage());
            return new PptContinueDecision(PptContinueDecision.Decision.PAUSE, "");
        }
    }

    /**
     * 从回复中剥离决策标记，保留面向用户的自然语言文本。
     */
    public String stripDecisionMarker(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        return DECISION_MARKER.matcher(response).replaceAll("").trim();
    }

    private static String extractDecisionJson(String response) {
        java.util.regex.Matcher matcher = DECISION_MARKER.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 继续执行状态机
     *
     * @param inst PPT 实例
     * @param sink 响应接收器
     * @param query 用户查询
     * @param thinkingBuffer 思考缓冲区
     */
    public void continueStateMachine(AiPptInst inst, Sinks.Many<String> sink, String query,
                                     StringBuilder thinkingBuffer) {
        PptStateStrategyFactory.getInstance().executeNextState(inst, sink, query, thinkingBuffer, this);
    }
}















