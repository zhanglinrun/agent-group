package com.linrun.trigger.agent.agent.pptx;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.agent.pptx.strategy.PptStateStrategyContext;
import com.linrun.trigger.agent.agent.pptx.strategy.PptStateStrategyFactory;
import com.linrun.trigger.agent.entity.record.pptx.*;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.linrun.trigger.agent.service.AiPptTemplateService;
import com.linrun.trigger.agent.service.PptPythonRenderService;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.domain.agent.file.adapter.FileStoragePort;
import com.linrun.trigger.agent.utils.ImageGenerationService;
import com.linrun.trigger.agent.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PPT Builder Agent
 * 实现基于模板驱动的PPT生成与修改功能，支持断点重连
 */
@Slf4j
public class PPTBuilderAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final AiPptInstService pptInstService;
    private final AiPptTemplateService pptTemplateService;
    private final PptPythonRenderService pythonRenderService;
    private final ImageGenerationService imageGenerationService;
    private final FileStoragePort fileStoragePort;

    private final List<ToolCallback> toolCallbacks;
    private final PptIntentRecognizer intentRecognizer;
    private final String executionMemoryPrompt;
    private PptStateStrategyContext strategyContext;

    public PPTBuilderAgent(ChatModel chatModel, List<ToolCallback> toolCallbacks, AiSessionService sessionService, AgentTaskManager taskManager,
                           AiPptInstService pptInstService, AiPptTemplateService pptTemplateService,
                           PptPythonRenderService pythonRenderService, ImageGenerationService imageGenerationService, FileStoragePort fileStoragePort) {
        this(chatModel, toolCallbacks, "", sessionService, taskManager, pptInstService, pptTemplateService, pythonRenderService, imageGenerationService, fileStoragePort);
    }

    public PPTBuilderAgent(ChatModel chatModel, List<ToolCallback> toolCallbacks, String executionMemoryPrompt,
                           AiSessionService sessionService, AgentTaskManager taskManager,
                           AiPptInstService pptInstService, AiPptTemplateService pptTemplateService,
                           PptPythonRenderService pythonRenderService, ImageGenerationService imageGenerationService, FileStoragePort fileStoragePort) {
        super("PPTBuilderAgent", chatModel, "pptx");
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.toolCallbacks = toolCallbacks;
        this.executionMemoryPrompt = executionMemoryPrompt == null ? "" : executionMemoryPrompt;

        // 依赖通过构造注入，避免运行期再从容器抓取
        this.pptInstService = pptInstService;
        this.pptTemplateService = pptTemplateService;
        this.pythonRenderService = pythonRenderService;
        this.imageGenerationService = imageGenerationService;
        this.fileStoragePort = fileStoragePort;

        this.chatClient = ChatClient.builder(chatModel).build();

        // 初始化意图识别器
        this.intentRecognizer = new PptIntentRecognizer(chatClient, pptInstService);

        // 初始化工具记录集合（并发安全：多个工具调用在 boundedElastic 线程并行写）
        this.usedTools = ConcurrentHashMap.newKeySet();
    }

    /**
     * 对外统一入口 - 流式执行PPT处理
     *
     * @param conversationId 会话ID
     * @param query          用户请求
     * @return 流式输出
     */
    @Override
    public Flux<String> execute(String conversationId, String query) {
        log.info("开始PPT处理: conversationId={}, query={}", conversationId, query);

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 保存当前会话ID
        this.currentConversationId = conversationId;

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();
        // 收集最终答案
        StringBuilder finalAnswerBuffer = new StringBuilder();

        try {
            // 初始化策略上下文
            initStrategyContext();

            // 1. 意图识别
            PptIntentResult intentResult = intentRecognizer.recognize(conversationId, query);
            log.info("意图识别结果: intent={}, reason={}", intentResult.getIntent(), intentResult.getReason());

            // 2. 保存对话
            if (sessionService != null) {
                AiSession savedSession = sessionService.saveQuestion(
                        SaveQuestionRequest.builder()
                                .sessionId(conversationId)
                                .question(query)
                                .build()
                );
                currentSessionId = savedSession.getId();
                strategyContext.setCurrentSessionId(currentSessionId);
            }

            // 3. 根据意图路由
            switch (intentResult.getIntent()) {
                case CREATE_PPT -> handleCreateIntent(conversationId, query, sink, thinkingBuffer);
                case MODIFY_PPT -> handleModifyIntent(conversationId, query, sink, thinkingBuffer);
                case RESUME_PPT -> handleResumeIntent(conversationId, query, sink, thinkingBuffer);
                default -> {
                    sink.tryEmitNext(createThinkingResponse("无法识别您的意图，请重新表述\n"));
                    sink.tryEmitComplete();
                }
            }
        } catch (Exception e) {
            log.error("PPT处理异常", e);
            sink.tryEmitError(e);
        }

        return sink.asFlux()
                .doOnNext(chunk -> {
                    // 解析 JSON，分离收集 text 与 thinking
                    try {
                        JsonNode json = JsonUtils.parse(chunk);
                        String type = json != null && json.has("type") ? json.get("type").asText() : null;
                        if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.has("content") ? json.get("content").asText() : "");
                        } else if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.has("content") ? json.get("content").asText() : "");
                        }
                    } catch (Exception e) {
                        // 解析失败，忽略
                    }
                })
                .doOnCancel(() -> taskManager.stopTask(conversationId))
                .doFinally(signalType -> {
                    log.info("PPT处理完成");
                    log.info("最终答案 {}", finalAnswerBuffer);
                    log.info("思考过程 {}", thinkingBuffer);

                    // 保存结果到会话
                    if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
                        UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                                .id(currentSessionId)
                                .answer(finalAnswerBuffer.toString())
                                .thinking(thinkingBuffer.toString())
                                .build();
                        sessionService.updateAnswer(request);
                        log.info("PPT结果已保存到会话: sessionId={}", currentConversationId);
                    }

                    // 正常结束只移除任务状态，用户点击停止才发送停止消息
                    taskManager.completeTask(conversationId);
                })
                .doOnError(err -> log.error("PPT处理流输出异常", err));
    }

    /**
     * 初始化策略上下文
     */
    private void initStrategyContext() {
        strategyContext = new PptStateStrategyContext(
                chatClient,
                chatModel,
                pptInstService,
                pptTemplateService,
                pythonRenderService,
                imageGenerationService,
                fileStoragePort,
                sessionService,
                taskManager,
                toolCallbacks,
                chatMemory,
                executionMemoryPrompt
        );
        strategyContext.setCurrentConversationId(currentConversationId);
        strategyContext.setCurrentSessionId(currentSessionId);
    }

    /**
     * 处理CREATE_PPT意图
     */
    private void handleCreateIntent(String conversationId, String query, Sinks.Many<String> sink, StringBuilder thinkingBuffer) {
        sink.tryEmitNext(createThinkingResponse("开始创建新的PPT...\n"));

        // 创建新的PPT实例
        AiPptInst inst = pptInstService.createInst(conversationId, query);

        // 启动状态机循环
        PptStateStrategyFactory.getInstance().executeNextState(inst, sink, query, thinkingBuffer, strategyContext);
    }

    /**
     * 处理MODIFY_PPT意图
     */
    private void handleModifyIntent(String conversationId, String query, Sinks.Many<String> sink, StringBuilder thinkingBuffer) {
        // 获取最新的PPT实例
        AiPptInst inst = pptInstService.getLatestInst(conversationId);

        if (inst == null) {
            String response = "当前会话中没有已生成的PPT，无法修改。请先生成一个PPT";
            sink.tryEmitNext(createTextResponse(response));
            saveResultToSession(null, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }

        // 读取已有ppt_schema
        String currentSchema = inst.getPptSchema();
        if (currentSchema == null || currentSchema.isEmpty()) {
            String response = "该PPT没有Schema数据，无法修改";
            sink.tryEmitNext(createTextResponse(response));
            saveResultToSession(null, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }

        sink.tryEmitNext(createThinkingResponse("正在修改PPT...\n"));

        // 设置修改操作标记和修改需求
        strategyContext.setModifyMode(true);
        strategyContext.setModifyQuery(query);

        // 生成修改后的Schema
        executeModifyFlow(inst, query, sink, thinkingBuffer);
    }

    /**
     * 处理RESUME_PPT意图（断点重连）
     */
    private void handleResumeIntent(String conversationId, String query, Sinks.Many<String> sink, StringBuilder thinkingBuffer) {
        // 获取最新的PPT实例
        AiPptInst inst = pptInstService.getLatestInst(conversationId);

        if (inst == null) {
            String response = "当前会话中没有PPT实例，无法继续。请先创建一个PPT";
            sink.tryEmitNext(createTextResponse(response));
            saveResultToSession(null, response, thinkingBuffer);
            sink.tryEmitComplete();
            return;
        }

        PptInstStatus status = inst.getStatusEnum();

        // 如果已经是SUCCESS状态，询问用户是否要修改
        if (status == PptInstStatus.SUCCESS) {
            sink.tryEmitNext(createThinkingResponse("当前PPT已经成功生成，如果您要修改，请说明具体修改需求。\n"));
            String response = "当前PPT已经成功生成。如果您需要修改，请说明具体的修改需求";
            sink.tryEmitNext(createTextResponse(response));
            sink.tryEmitComplete();
            return;
        }

        sink.tryEmitNext(createThinkingResponse("正在从状态 " + status + " 继续执行 PPT 生成...\n"));

        // 直接从当前状态执行状态机
        PptStateStrategyFactory.getInstance().executeNextState(inst, sink, query, thinkingBuffer, strategyContext);
    }

    /**
     * 修改PPT流程
     */
    private void executeModifyFlow(AiPptInst inst, String query, Sinks.Many<String> sink, StringBuilder thinkingBuffer) {
        sink.tryEmitNext(createThinkingResponse("正在分析修改需求...\n"));
        sink.tryEmitNext(createThinkingResponse("正在修改PPT内容...\n"));

        // 直接调用 SchemaStrategy 继续执行（会处理图片生成、渲染等）
        PptStateStrategyFactory.getInstance().executeSchemaStrategy(inst, sink, query, thinkingBuffer, strategyContext);
    }

    /**
     * 保存结果到会话
     */
    private void saveResultToSession(AiPptInst inst, String result, StringBuilder thinkingBuffer) {
        if (sessionService == null || currentSessionId == null) {
            return;
        }

        try {
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(result)
                    .thinking(thinkingBuffer.toString())
                    .build();
            sessionService.updateAnswer(request);
            String conversationId = inst != null ? inst.getConversationId() : currentConversationId;
            log.info("PPT生成结果已保存到会话: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("保存结果到会话失败", e);
        }
    }
}















