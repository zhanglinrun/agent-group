package com.linrun.trigger.http.agent;

import com.linrun.trigger.http.agent.support.AgentContextResolver;
import com.linrun.trigger.http.agent.support.AgentSessionService;
import com.linrun.trigger.http.agent.support.AgentFileService;
import com.linrun.trigger.http.agent.support.AgentCapabilityService;
import com.linrun.trigger.http.agent.support.AgentWebSearchMcpClient;
import com.linrun.trigger.http.agent.support.SkillsRuntimeResolver;
import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.agent.deepresearch.PlanExecuteAgent;
import com.linrun.trigger.agent.agent.deepresearch.runtime.LedgerAgentMemoryService;
import com.linrun.trigger.agent.agent.file.FileReactAgent;
import com.linrun.trigger.agent.agent.pptx.PPTBuilderAgent;
import com.linrun.trigger.agent.agent.skills.SkillsReactAgent;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeTools;
import com.linrun.trigger.agent.agent.skills.runtime.ManualSkillRegistry;
import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.agent.websearch.WebSearchReactAgent;
import com.linrun.trigger.agent.checkpoint.AgentCheckpointStore;
import com.linrun.trigger.config.AgentDeepRuntimeProperties;
import com.linrun.trigger.agent.context.ContextPolicy;
import com.linrun.trigger.agent.context.AgentTokenUsageRecorder;
import com.linrun.trigger.agent.context.UsageRecordingChatModel;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.linrun.trigger.agent.service.AiPptTemplateService;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.trigger.agent.service.FileInfoService;
import com.linrun.trigger.agent.service.FileManageService;
import com.linrun.domain.agent.file.adapter.FileStoragePort;
import com.linrun.trigger.agent.service.PptPythonRenderService;
import com.linrun.trigger.agent.utils.ImageGenerationService;
import com.linrun.trigger.agent.tool.FileContentService;
import com.linrun.trigger.agent.tool.AgentToolCallbackFactory;
import com.linrun.trigger.agent.tool.SearchTool;
import com.linrun.trigger.agent.tool.ToolMergeUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.memory.service.UserAgentMemoryService;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.types.exception.AppException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentNativeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentNativeService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiSessionService sessionService;
    private final AgentTaskManager taskManager;
    private final FileContentService fileContentService;
    private final FileManageService fileManageService;
    private final FileInfoService fileInfoService;
    private final AiPptInstService aiPptInstService;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AgentExternalSearchService externalSearchService;
    private final AgentToolCallbackFactory agentToolCallbackFactory;
    private final AgentAdminConfigHandler agentAdminConfigHandler;
    private final AgentContextResolver agentContextResolver;
    private final AgentSessionService agentSessionService;
    private final AgentFileService agentFileService;
    private final AgentWebSearchMcpClient webSearchMcpClient;
    private final SkillsRuntimeResolver skillsRuntimeResolver;
    private final AgentCapabilityService capabilityService;
    private final ObjectProvider<AgentCheckpointStore> checkpointStoreProvider;
    private final ObjectProvider<CircuitBreaker> llmCircuitBreakerProvider;
    private final ObjectProvider<Retry> llmRetryProvider;
    private final ObjectProvider<AiPptTemplateService> aiPptTemplateServiceProvider;
    private final ObjectProvider<PptPythonRenderService> pptPythonRenderServiceProvider;
    private final ObjectProvider<ImageGenerationService> imageGenerationServiceProvider;
    private final ObjectProvider<FileStoragePort> fileStoragePortProvider;
    private final ObjectProvider<AgentExecutionLedgerService> agentExecutionLedgerServiceProvider;
    private final ObjectProvider<UserAgentMemoryService> userAgentMemoryServiceProvider;
    private final AgentDeepRuntimeProperties deepRuntimeProperties;

    @Value("${spring.ai.openai.chat.options.model:qwen3.7-plus}")
    private String defaultChatModel;

    public AgentNativeService(@Qualifier("openAiChatModel") ObjectProvider<ChatModel> chatModelProvider,
                                  AiSessionService sessionService,
                                  AgentTaskManager taskManager,
                                  FileContentService fileContentService,
                                  FileManageService fileManageService,
                                  FileInfoService fileInfoService,
                                  AiPptInstService aiPptInstService,
                                  UserAccountService userAccountService,
                                  UserQuotaService userQuotaService,
                                  AgentExternalSearchService externalSearchService,
                                  AgentToolCallbackFactory agentToolCallbackFactory,
                                  AgentAdminConfigHandler agentAdminConfigHandler,
                                  AgentContextResolver agentContextResolver,
                                  AgentSessionService agentSessionService,
                                  AgentFileService agentFileService,
                                  AgentWebSearchMcpClient webSearchMcpClient,
                                  SkillsRuntimeResolver skillsRuntimeResolver,
                                  AgentCapabilityService capabilityService,
                                  ObjectProvider<AgentCheckpointStore> checkpointStoreProvider,
                                  @Qualifier("llmChatCircuitBreaker")
                                  ObjectProvider<CircuitBreaker> llmCircuitBreakerProvider,
                                  @Qualifier("llmChatRetry")
                                  ObjectProvider<Retry> llmRetryProvider,
                                  ObjectProvider<AiPptTemplateService> aiPptTemplateServiceProvider,
                                  ObjectProvider<PptPythonRenderService> pptPythonRenderServiceProvider,
                                  ObjectProvider<ImageGenerationService> imageGenerationServiceProvider,
                                  ObjectProvider<FileStoragePort> fileStoragePortProvider,
                                  ObjectProvider<AgentExecutionLedgerService> agentExecutionLedgerServiceProvider,
                                  ObjectProvider<UserAgentMemoryService> userAgentMemoryServiceProvider,
                                  AgentDeepRuntimeProperties deepRuntimeProperties) {
        this.chatModelProvider = chatModelProvider;
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.fileContentService = fileContentService;
        this.fileManageService = fileManageService;
        this.fileInfoService = fileInfoService;
        this.aiPptInstService = aiPptInstService;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.externalSearchService = externalSearchService;
        this.agentToolCallbackFactory = agentToolCallbackFactory;
        this.agentAdminConfigHandler = agentAdminConfigHandler;
        this.agentContextResolver = agentContextResolver;
        this.agentSessionService = agentSessionService;
        this.agentFileService = agentFileService;
        this.webSearchMcpClient = webSearchMcpClient;
        this.skillsRuntimeResolver = skillsRuntimeResolver;
        this.capabilityService = capabilityService;
        this.checkpointStoreProvider = checkpointStoreProvider;
        this.llmCircuitBreakerProvider = llmCircuitBreakerProvider;
        this.llmRetryProvider = llmRetryProvider;
        this.aiPptTemplateServiceProvider = aiPptTemplateServiceProvider;
        this.pptPythonRenderServiceProvider = pptPythonRenderServiceProvider;
        this.imageGenerationServiceProvider = imageGenerationServiceProvider;
        this.fileStoragePortProvider = fileStoragePortProvider;
        this.agentExecutionLedgerServiceProvider = agentExecutionLedgerServiceProvider;
        this.userAgentMemoryServiceProvider = userAgentMemoryServiceProvider;
        this.deepRuntimeProperties = deepRuntimeProperties;
    }

    public Flux<String> stream(String token,
                               String agentType,
                               String query,
                               String conversationId,
                               String fileId,
                               boolean webSearchEnabled,
                               String llmBaseUrl,
                               String llmApiKey,
                               String llmModel) {
        return stream(token, agentType, query, conversationId, fileId,
                webSearchEnabled, llmBaseUrl, llmApiKey, llmModel, "", "");
    }

    public Flux<String> stream(String token,
                               String agentType,
                               String query,
                               String conversationId,
                               String fileId,
                               boolean webSearchEnabled,
                               String llmBaseUrl,
                               String llmApiKey,
                               String llmModel,
                               String executionMemoryPrompt) {
        return stream(token, agentType, query, conversationId, fileId,
                webSearchEnabled, llmBaseUrl, llmApiKey, llmModel, executionMemoryPrompt, "");
    }

    public Flux<String> stream(String token,
                               String agentType,
                               String query,
                               String conversationId,
                               String fileId,
                               boolean webSearchEnabled,
                               String llmBaseUrl,
                               String llmApiKey,
                               String llmModel,
                               String executionMemoryPrompt,
                               String continueTraceId) {
        UserAccount user = user(token);
        String safeAgentType = normalizeAgentType(agentType);
        String safeConversationId = StringUtils.hasText(conversationId) ? conversationId.trim() : "S" + System.currentTimeMillis();
        String internalConversationId = internalConversationId(user.getUserId(), safeConversationId);
        RuntimeModelSelection modelSelection = chatModel(user.getUserId(), internalConversationId, llmBaseUrl, llmApiKey, llmModel);
        ChatModel runtimeChatModel = modelSelection.chatModel();
        BigDecimal quotaCost = userQuotaService.estimatePreCheckCost(safeAgentType, modelSelection.customModelUsed());
        userQuotaService.assertEnoughQuota(user.getUserId(), quotaCost, modelSelection.customModelUsed());
        validateFileAccess(user.getUserId(), internalConversationId, fileId);
        long startNanos = System.nanoTime();
        StringBuilder observedContent = new StringBuilder();
        AgentTokenUsageRecorder.start(internalConversationId);
        ToolCallback[] searchTools = webSearchEnabled ? webSearchMcpClient.getToolCallbacks() : new ToolCallback[0];
        String memoryPrompt = joinPrompts(agentAdminRuntimePrompt(safeAgentType), executionMemoryPrompt);

        Flux<String> agentFlux = switch (safeAgentType) {
            case "file" -> withMemory(initFileReactAgent(user.getUserId(), internalConversationId, runtimeChatModel, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "ppt" -> withMemory(initPPTBuilderAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .execute(internalConversationId, query);
            case "deep" -> withMemory(initPlanExecuteAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query);
            case "image" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "image", memoryPrompt, continueTraceId), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("image", query), fileId);
            case "data" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "data", memoryPrompt, continueTraceId), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("data", query), fileId);
            case "trade-diagnosis" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "trade-diagnosis", memoryPrompt, continueTraceId), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("trade-diagnosis", query), fileId);
            case "skills" -> withMemory(initSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt, continueTraceId), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "manual-skills" -> withMemory(initManualSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt, continueTraceId), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            default -> {
                if (StringUtils.hasText(fileId)) {
                    yield withMemory(initFileReactAgent(user.getUserId(), internalConversationId, runtimeChatModel, memoryPrompt), internalConversationId)
                            .stream(internalConversationId, query, fileId);
                }
                yield withMemory(initWebSearchAgent(internalConversationId, runtimeChatModel,
                                searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                        .stream(internalConversationId, query);
            }
        };

        AtomicBoolean consumed = new AtomicBoolean(false);
        return agentFlux.doOnNext(observedContent::append)
                .doFinally(signalType -> {
                    try {
                        AgentTokenUsageRecorder.Snapshot tokenUsage = AgentTokenUsageRecorder.snapshot(internalConversationId);
                        if (consumed.compareAndSet(false, true)
                                && shouldConsumeQuota(signalType, observedContent, tokenUsage)) {
                            long latencyMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
                            consumeQuota(user.getUserId(), safeConversationId, safeAgentType, query,
                                    observedContent.toString(), latencyMillis, tokenUsage, modelSelection.customModelUsed());
                            fillAgentType(internalConversationId, safeAgentType);
                        }
                    } finally {
                        AgentTokenUsageRecorder.clear(internalConversationId);
                    }
                });
    }

    public void saveDeterministicTurn(String token,
                                      String agentType,
                                      String query,
                                      String conversationId,
                                      String fileId,
                                      String answer,
                                      long latencyMillis) {
        agentSessionService.saveDeterministicTurn(token, agentType, query, conversationId, fileId, answer, latencyMillis);
    }

    public FileInfo upload(String token, MultipartFile file, String conversationId) {
        return agentFileService.upload(token, file, conversationId);
    }

    public FileInfo getFileInfo(String token, String fileId) {
        return agentFileService.getFileInfo(token, fileId);
    }

    public String getFileContent(String token, String fileId) {
        return agentFileService.getFileContent(token, fileId);
    }

    public void deleteFile(String token, String fileId) {
        agentFileService.deleteFile(token, fileId);
    }

    public List<FileInfo> listFiles(String token) {
        return agentFileService.listFiles(token);
    }

    public boolean fileExists(String token, String fileId) {
        return agentFileService.fileExists(token, fileId);
    }

    public boolean stop(String token, String conversationId) {
        return agentSessionService.stop(token, conversationId);
    }

    public List<AiSession> querySessions(String token, int pageNum, int pageSize) {
        return agentSessionService.querySessions(token, pageNum, pageSize);
    }

    public long countSessions(String token) {
        return agentSessionService.countSessions(token);
    }

    public List<AiSession> querySessionMessages(String token, String conversationId) {
        return agentSessionService.querySessionMessages(token, conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String token, String conversationId) {
        agentSessionService.deleteSession(token, conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime rollbackSessionFromMessage(String token, String conversationId, String messageId) {
        return agentSessionService.rollbackSessionFromMessage(token, conversationId, messageId);
    }

    public Map<String, Object> capabilities() {
        return capabilityService.capabilities();
    }
    public String externalConversationId(String userId, String sessionId) {
        String prefix = userId + ":";
        return sessionId != null && sessionId.startsWith(prefix) ? sessionId.substring(prefix.length()) : sessionId;
    }
    private WebSearchReactAgent initWebSearchAgent(String conversationId, ChatModel chatModel,
                                                   ToolCallback[] searchTools,
                                                   boolean webSearchEnabled,
                                                   String executionMemoryPrompt) {
        return WebSearchReactAgent.builder()
                .name("web react")
                .chatModel(chatModel)
                .tools(searchTools)
                .systemPrompt(joinPrompts(webSearchEnabled ? "" : webSearchDisabledPrompt(), executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(5)
                .build();
    }

    private FileReactAgent initFileReactAgent(String userId, String conversationId, ChatModel chatModel,
                                              String executionMemoryPrompt) {
        List<ToolCallback> tools = Arrays.asList(fileContentToolCallbacks(userId));
        return FileReactAgent.builder()
                .name("file react")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(executionMemoryPrompt)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .fileContentService(fileContentService)
                .build();
    }

    private PPTBuilderAgent initPPTBuilderAgent(String userId, String conversationId, ChatModel chatModel,
                                                ToolCallback[] searchTools,
                                                boolean webSearchEnabled,
                                                String executionMemoryPrompt) {
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                agentToolCallbacks("ppt", userId, conversationId, webSearchEnabled),
                skillsRuntimeResolver.skillsToolCallbacks()
        );
        return new PPTBuilderAgent(chatModel, Arrays.asList(tools), executionMemoryPrompt, sessionService, taskManager,
                aiPptInstService, aiPptTemplateServiceProvider.getObject(),
                pptPythonRenderServiceProvider.getObject(), imageGenerationServiceProvider.getObject(),
                fileStoragePortProvider.getObject());
    }

    private PlanExecuteAgent initPlanExecuteAgent(String userId, String conversationId, ChatModel chatModel,
                                                  ToolCallback[] searchTools,
                                                  boolean webSearchEnabled,
                                                  String executionMemoryPrompt) {
        String outputDirectory = skillsRuntimeResolver.sessionSkillsOutputDirectory(conversationId);
        SkillManager skillManager = skillsRuntimeResolver.manualSkillManager();
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                agentToolCallbacks("deep", userId, conversationId, webSearchEnabled),
                skillsRuntimeResolver.manualSkillToolCallbacks(skillManager),
                SkillRuntimeTools.create(skillsRuntimeResolver.resolvedSkillsDirectory(),
                        skillsRuntimeResolver.projectRoot().toString(), outputDirectory)
        );
        return PlanExecuteAgent.builder()
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(skillsRuntimeResolver.skillRuntimePrompt(outputDirectory, webSearchEnabled),
                        executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(3)
                .skillRegistry(skillManager == null ? null : new ManualSkillRegistry(skillManager))
                .memoryService(deepMemoryService())
                .deepRuntimeProperties(deepRuntimeProperties)
                .build();
    }

    private LedgerAgentMemoryService deepMemoryService() {
        AgentExecutionLedgerService ledgerService = agentExecutionLedgerServiceProvider == null
                ? null
                : agentExecutionLedgerServiceProvider.getIfAvailable();
        UserAgentMemoryService userMemoryService = userAgentMemoryServiceProvider == null
                ? null
                : userAgentMemoryServiceProvider.getIfAvailable();
        return ledgerService == null && userMemoryService == null
                ? null
                : new LedgerAgentMemoryService(ledgerService, userMemoryService, deepRuntimeProperties);
    }

    private SkillsReactAgent initSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                  ToolCallback[] searchTools,
                                                  boolean webSearchEnabled,
                                                  String executionMemoryPrompt,
                                                  String continueTraceId) {
        String outputDirectory = skillsRuntimeResolver.sessionSkillsOutputDirectory(conversationId);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                agentToolCallbacks("skills", userId, conversationId, webSearchEnabled),
                skillsRuntimeResolver.skillsToolCallbacks(),
                SkillRuntimeTools.create(skillsRuntimeResolver.resolvedSkillsDirectory(), skillsRuntimeResolver.projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name("skills")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(skillsRuntimeResolver.skillRuntimePrompt(outputDirectory, webSearchEnabled), executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .contextPolicy(ContextPolicy.defaults())
                .checkpointStore(availableCheckpointStore())
                .continueTraceId(continueTraceId)
                .build();
    }

    private SkillsReactAgent initWorkspaceReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                     ToolCallback[] searchTools,
                                                     boolean webSearchEnabled,
                                                     String workspace,
                                                     String executionMemoryPrompt,
                                                     String continueTraceId) {
        String outputDirectory = skillsRuntimeResolver.sessionSkillsOutputDirectory(conversationId);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                agentToolCallbacks(workspace, userId, conversationId, webSearchEnabled),
                skillsRuntimeResolver.skillsToolCallbacks(),
                SkillRuntimeTools.create(skillsRuntimeResolver.resolvedSkillsDirectory(), skillsRuntimeResolver.projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name(workspace + "-workspace")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(skillsRuntimeResolver.workspaceRuntimePrompt(outputDirectory, webSearchEnabled, workspace),
                        executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(8)
                .contextPolicy(ContextPolicy.defaults())
                .checkpointStore(availableCheckpointStore())
                .continueTraceId(continueTraceId)
                .build();
    }

    private SkillsReactAgent initManualSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                        ToolCallback[] searchTools,
                                                        boolean webSearchEnabled,
                                                        String executionMemoryPrompt,
                                                        String continueTraceId) {
        String outputDirectory = skillsRuntimeResolver.sessionSkillsOutputDirectory(conversationId);
        SkillManager skillManager = skillsRuntimeResolver.manualSkillManager();
        ToolCallback[] manualSkillTools = skillsRuntimeResolver.manualSkillToolCallbacks(skillManager);
        String skillsPrompt = skillManager == null ? "" : skillManager.formatPrompt();
        String runtimePrompt = skillsRuntimeResolver.skillRuntimePrompt(outputDirectory, webSearchEnabled);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                agentToolCallbacks("manual-skills", userId, conversationId, webSearchEnabled),
                manualSkillTools,
                SkillRuntimeTools.create(skillsRuntimeResolver.resolvedSkillsDirectory(), skillsRuntimeResolver.projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name("manual-skills")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(skillsPrompt, runtimePrompt, executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(10)
                .contextPolicy(ContextPolicy.defaults())
                .checkpointStore(availableCheckpointStore())
                .continueTraceId(continueTraceId)
                .build();
    }

    private AgentCheckpointStore availableCheckpointStore() {
        return checkpointStoreProvider == null ? null : checkpointStoreProvider.getIfAvailable();
    }

    private ToolCallback[] fileContentToolCallbacks(String userId) {
        return ToolCallbacks.from(new OwnedFileContentTool(userId));
    }

    private ToolCallback[] agentToolCallbacks(String scene,
                                                 String userId,
                                                 String conversationId,
                                                 boolean webAccessEnabled) {
        if (agentToolCallbackFactory == null) {
            return new ToolCallback[0];
        }
        try {
            return agentToolCallbackFactory.create(scene, userId, conversationId, webAccessEnabled);
        } catch (Exception e) {
            LOGGER.warn("agent-runtime agent tool init skipped, scene={}, reason={}",
                    scene, e.getClass().getSimpleName());
            return new ToolCallback[0];
        }
    }

    private String workspaceQuery(String workspace, String query) {
        String safeQuery = StringUtils.hasText(query) ? query.trim() : "";
        if ("image".equals(workspace)) {
            return """
                    Handle this as an image-generation workspace request. Call image_generation when needed and record artifacts.
                    User request:
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("data".equals(workspace)) {
            return """
                    Handle this as a data-analysis workspace request. Prefer data_analysis, table_rag, or nl2sql.
                    User request:
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("trade".equals(workspace)) {
            return """
                    Handle this as a trade-data workspace request. Explain only order, group, payment, refund, and quota-flow state.
                    User request:
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("trade-diagnosis".equals(workspace)) {
            return """
                    Handle this as a read-only trade diagnosis request. Use list_trade_orders and diagnose_trade_order to aggregate order, payment, refund and quota-flow facts, then give a consistency conclusion and handling advice. Never place orders, grant quota, or issue refunds.
                    User request:
                    %s
                    """.formatted(safeQuery).trim();
        }
        return safeQuery;
    }
    String agentAdminRuntimePrompt(String agentType) {
        if (agentAdminConfigHandler == null) {
            return "";
        }
        try {
            String safeAgentType = normalizeAgentType(agentType);
            String prompt = joinPrompts(
                    agentAdminCategoryPrompt("system_prompt", "system prompt", safeAgentType),
                    agentAdminCategoryPrompt("advisor", "advisor config", safeAgentType),
                    agentAdminCategoryPrompt("rag_order", "RAG order", safeAgentType),
                    "image".equals(safeAgentType)
                            ? agentAdminCategoryPrompt("draw_config", "image config", safeAgentType)
                            : "");
            if (!StringUtils.hasText(prompt)) {
                return "";
            }
            return "## Agent admin runtime config\n" + prompt;
        } catch (Exception e) {
            LOGGER.warn("agent admin runtime prompt degraded, reason={}", e.getClass().getSimpleName());
            return "";
        }
    }
    private String agentAdminCategoryPrompt(String category, String label, String agentType) {
        List<Map<String, Object>> configs = agentAdminConfigHandler.listConfigs(category, true).stream()
                .filter(config -> agentAdminConfigApplies(config, agentType))
                .filter(config -> StringUtils.hasText(text(config.get("content"))))
                .limit(8)
                .toList();
        if (configs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("### ").append(label);
        for (Map<String, Object> config : configs) {
            builder.append("\n- [")
                    .append(text(config.get("configId")))
                    .append("] ")
                    .append(defaultText(config.get("name"), text(config.get("configId"))))
                    .append("\n")
                    .append(limitPromptContent(config.get("content")));
        }
        return builder.toString();
    }

    private boolean agentAdminConfigApplies(Map<String, Object> config, String agentType) {
        Map<String, Object> metadata = mapValue(config.get("metadata"));
        if (metadata.isEmpty()) {
            return true;
        }
        List<String> scopedKeys = List.of("agentType", "agentId", "taskType", "taskTypes",
                "workspace", "workspaces", "scene", "scenes", "agents");
        boolean hasScopedKey = false;
        for (String key : scopedKeys) {
            if (!metadata.containsKey(key)) {
                continue;
            }
            hasScopedKey = true;
            if (agentAdminMetadataMatches(metadata.get(key), agentType)) {
                return true;
            }
        }
        return !hasScopedKey;
    }

    private boolean agentAdminMetadataMatches(Object value, String agentType) {
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(item -> agentAdminMetadataMatches(item, agentType));
        }
        String normalized = text(value).toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String safeAgentType = normalizeAgentType(agentType);
        String workspace = workspaceForAgentType(safeAgentType);
        return normalized.contains(safeAgentType) || normalized.contains(workspace);
    }

    private String workspaceForAgentType(String agentType) {
        return normalizeAgentType(agentType);
    }

    private String limitPromptContent(Object value) {
        String content = text(value);
        return content.length() <= 1200 ? content : content.substring(0, 1200);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private String webSearchDisabledPrompt() {
        return """
                ## 联网搜索状态：        本轮未开启联网搜索。不要调用搜索工具，也不要输出 ToolCall/search 文本。        普通知识和上下文足够时直接回答；确实需要实时信息时，请提示用户开启联网搜索后重试。                """;
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

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T extends BaseAgent> T withMemory(T agent, String conversationId) {
        ChatMemory persistentMemory = agent.createPersistentChatMemory(conversationId, 30);
        agent.setChatMemory(persistentMemory);
        return agent;
    }

    private RuntimeModelSelection chatModel(String userId,
                                            String conversationId,
                                            String llmBaseUrl,
                                            String llmApiKey,
                                            String llmModel) {
        Optional<UserModelConfig> storedConfig = userQuotaService.queryRuntimeModelConfig(userId);
        if (storedConfig.isPresent()) {
            UserModelConfig config = storedConfig.get();
            ChatModel chatModel = customChatModel(config.getBaseUrl(), config.getApiKey(), config.getModel());
            return new RuntimeModelSelection(wrapUsage(chatModel, conversationId), true);
        }
        if (hasCustomModelConfig(llmBaseUrl, llmApiKey, llmModel)) {
            ChatModel chatModel = customChatModel(llmBaseUrl, llmApiKey, llmModel);
            return new RuntimeModelSelection(wrapUsage(chatModel, conversationId), true);
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AppException("AGENT_0007", "Chat model is not configured.");
        }
        return new RuntimeModelSelection(wrapUsage(chatModel, conversationId), false);
    }

    private UsageRecordingChatModel wrapUsage(ChatModel chatModel, String conversationId) {
        return new UsageRecordingChatModel(chatModel, conversationId,
                llmCircuitBreakerProvider.getIfAvailable(), llmRetryProvider.getIfAvailable());
    }

    private ChatModel customChatModel(String llmBaseUrl, String llmApiKey, String llmModel) {
        if (!StringUtils.hasText(llmBaseUrl) || !StringUtils.hasText(llmApiKey)) {
            throw new AppException("AGENT_0010", "Custom model requires API base URL and API key.");
        }
        String baseUrl = normalizeCustomBaseUrl(llmBaseUrl);
        String model = StringUtils.hasText(llmModel) ? llmModel.trim() : defaultChatModel;
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(0.2d)
                .model(model)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(new SimpleApiKey(llmApiKey.trim()))
                        .build())
                .defaultOptions(options)
                .build();
    }

    private String normalizeCustomBaseUrl(String llmBaseUrl) {
        String text = llmBaseUrl == null ? "" : llmBaseUrl.trim();
        if (text.regionMatches(true, 0, "ttps://", 0, "ttps://".length())) {
            text = "h" + text;
        }
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            text = "https://" + text.replaceFirst("^/+", "");
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (Exception e) {
            throw new AppException("AGENT_0011", "Custom model API base URL is invalid.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || !StringUtils.hasText(host)) {
            throw new AppException("AGENT_0011", "Custom model API base URL must use HTTPS.");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            throw new AppException("AGENT_0011", "Custom model API base URL cannot point to a private or local address.");
        }
        String normalized = text.replaceAll("/+$", "");
        return normalized.endsWith("/v1") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }
    private boolean hasCustomModelConfig(String llmBaseUrl, String llmApiKey, String llmModel) {
        return StringUtils.hasText(llmBaseUrl) || StringUtils.hasText(llmApiKey) || StringUtils.hasText(llmModel);
    }

    private UserAccount user(String token) {
        return agentContextResolver.user(token);
    }

    private void validateFileAccess(String userId, String internalConversationId, String fileId) {
        agentContextResolver.validateFileAccess(userId, internalConversationId, fileId);
    }

    private void assertOwnedFile(String userId, FileInfo fileInfo) {
        agentContextResolver.assertOwnedFile(userId, fileInfo);
    }

    private void consumeQuota(String userId,
                              String conversationId,
                              String agentType,
                              String query,
                              String observedContent,
                              long latencyMillis,
                              AgentTokenUsageRecorder.Snapshot tokenUsage,
                              boolean customModelUsed) {
        TokenUsageMetrics usage = hasRealUsage(tokenUsage)
                ? new TokenUsageMetrics(tokenUsage.promptTokens(), tokenUsage.completionTokens(),
                tokenUsage.totalTokens(), BigDecimal.ZERO)
                : estimateTokenUsage(query, observedContent);
        String model = hasRealUsage(tokenUsage) && StringUtils.hasText(tokenUsage.model())
                ? tokenUsage.model()
                : "agent-workspace-estimated";
        userQuotaService.consumeForAgentTask(userId, conversationId, agentType,
                usage, model, latencyMillis, customModelUsed);
    }

    private boolean hasRealUsage(AgentTokenUsageRecorder.Snapshot tokenUsage) {
        return tokenUsage != null && tokenUsage.hasUsage();
    }

    private boolean shouldConsumeQuota(SignalType signalType,
                                       StringBuilder observedContent,
                                       AgentTokenUsageRecorder.Snapshot tokenUsage) {
        return SignalType.ON_COMPLETE.equals(signalType)
                || (observedContent != null && !observedContent.isEmpty())
                || hasRealUsage(tokenUsage);
    }

    private TokenUsageMetrics estimateTokenUsage(String query, String observedContent) {
        long promptTokens = estimateTokens(query);
        long completionTokens = estimateTokens(observedContent);
        return new TokenUsageMetrics(promptTokens, completionTokens, promptTokens + completionTokens, BigDecimal.ZERO);
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

    private void fillAgentType(String internalConversationId, String agentType) {
        agentSessionService.fillAgentType(internalConversationId, agentType);
    }

    private String internalConversationId(String userId, String conversationId) {
        return agentContextResolver.internalConversationId(userId, conversationId);
    }

    private String normalizeAgentType(String agentType) {
        return AgentContextResolver.normalizeAgentType(agentType);
    }
    private String message(Throwable error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return "Tool execution failed.";
        }
        String message = error.getMessage();
        String lower = message.toLowerCase();
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "Duplicate quota flow record; the request may have already been processed.";
        }
        return message;
    }

    private class OwnedFileContentTool {
        private final String userId;

        private OwnedFileContentTool(String userId) {
            this.userId = userId;
        }

        @Tool(description = "Load content from a user-owned uploaded file by file ID and question.")
        public String loadContent(
                @ToolParam(description = "File ID") String fileId,
                @ToolParam(description = "Question used to select relevant file content") String question) {
            try {
                FileInfo fileInfo = fileManageService.getFileInfo(fileId);
                assertOwnedFile(userId, fileInfo);
                return fileContentService.loadContent(fileId, question);
            } catch (Exception e) {
                LOGGER.warn("agent-runtime file tool access denied or failed, fileId={}, reason={}", fileId, e.getClass().getSimpleName());
                return message(e);
            }
        }
    }
    private record RuntimeModelSelection(ChatModel chatModel, boolean customModelUsed) {
    }
}
