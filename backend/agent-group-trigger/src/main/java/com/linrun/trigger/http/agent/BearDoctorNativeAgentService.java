package com.linrun.trigger.http.agent;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.agent.deepresearch.PlanExecuteAgent;
import com.linrun.trigger.agent.agent.file.FileReactAgent;
import com.linrun.trigger.agent.agent.pptx.PPTBuilderAgent;
import com.linrun.trigger.agent.agent.skills.SkillsReactAgent;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeTools;
import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.config.SkillConfig;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.agent.skills.manual.tool.GlobSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.GrepSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ListSkillDirectoryTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ReadSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ReadSkillTool;
import com.linrun.trigger.agent.agent.websearch.WebSearchReactAgent;
import com.linrun.trigger.agent.context.ContextPolicy;
import com.linrun.trigger.agent.context.BearDoctorTokenUsageRecorder;
import com.linrun.trigger.agent.context.UsageRecordingChatModel;
import com.linrun.trigger.agent.entity.AiSession;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.vo.SaveQuestionRequest;
import com.linrun.trigger.agent.entity.vo.UpdateAnswerRequest;
import com.linrun.trigger.agent.mapper.AiSessionMapper;
import com.linrun.trigger.agent.service.AgentTaskManager;
import com.linrun.trigger.agent.service.AiPptInstService;
import com.linrun.trigger.agent.service.AiSessionService;
import com.linrun.trigger.agent.service.FileInfoService;
import com.linrun.trigger.agent.service.FileManageService;
import com.linrun.trigger.agent.tool.FileContentService;
import com.linrun.trigger.agent.tool.AcademicToolCallbackFactory;
import com.linrun.trigger.agent.tool.SearchTool;
import com.linrun.trigger.agent.tool.SkillsTool;
import com.linrun.trigger.agent.tool.ToolMergeUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.agent.conversation.model.TokenUsageMetrics;
import com.linrun.types.exception.AppException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BearDoctorNativeAgentService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(BearDoctorNativeAgentService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiSessionService sessionService;
    private final AgentTaskManager taskManager;
    private final FileContentService fileContentService;
    private final FileManageService fileManageService;
    private final FileInfoService fileInfoService;
    private final AiPptInstService aiPptInstService;
    private final AiSessionMapper aiSessionMapper;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AcademicExternalSearchService externalSearchService;
    private final AcademicToolCallbackFactory academicToolCallbackFactory;
    private final AgentAdminConfigHandler agentAdminConfigHandler;
    private final ObjectProvider<McpAdminHandler> mcpAdminHandler;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${tavily.mcp-url:}")
    private String tavilyMcpUrl;

    @Value("${skills.directory:skills}")
    private String skillsDirectory;

    @Value("${skills.output-directory:outputs}")
    private String skillsOutputDirectory;

    @Value("${spring.ai.openai.chat.options.model:qwen3.7-plus}")
    private String defaultChatModel;

    @Value("${agent.group.reactor-tool.enabled:false}")
    private boolean reactorToolEnabled;

    private ToolCallback[] webSearchToolCallbacks = new ToolCallback[0];
    private String webSearchStatus = "missing-config";

    public BearDoctorNativeAgentService(ObjectProvider<ChatModel> chatModelProvider,
                                  AiSessionService sessionService,
                                  AgentTaskManager taskManager,
                                  FileContentService fileContentService,
                                  FileManageService fileManageService,
                                  FileInfoService fileInfoService,
                                  AiPptInstService aiPptInstService,
                                  AiSessionMapper aiSessionMapper,
                                  UserAccountService userAccountService,
                                  UserQuotaService userQuotaService,
                                  AcademicExternalSearchService externalSearchService,
                                  AcademicToolCallbackFactory academicToolCallbackFactory,
                                  AgentAdminConfigHandler agentAdminConfigHandler,
                                  ObjectProvider<McpAdminHandler> mcpAdminHandler) {
        this.chatModelProvider = chatModelProvider;
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.fileContentService = fileContentService;
        this.fileManageService = fileManageService;
        this.fileInfoService = fileInfoService;
        this.aiPptInstService = aiPptInstService;
        this.aiSessionMapper = aiSessionMapper;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.externalSearchService = externalSearchService;
        this.academicToolCallbackFactory = academicToolCallbackFactory;
        this.agentAdminConfigHandler = agentAdminConfigHandler;
        this.mcpAdminHandler = mcpAdminHandler;
    }

    @Override
    public void afterPropertiesSet() {
        initWebSearchToolCallbacks();
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
                webSearchEnabled, llmBaseUrl, llmApiKey, llmModel, "");
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
        BearDoctorTokenUsageRecorder.start(internalConversationId);
        ToolCallback[] searchTools = webSearchEnabled ? webSearchToolCallbacks : new ToolCallback[0];
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
                            searchTools, webSearchEnabled, "image", memoryPrompt), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("image", query), fileId);
            case "data" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "data", memoryPrompt), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("data", query), fileId);
            case "mrag" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "mrag", memoryPrompt), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("mrag", query), fileId);
            case "skills" -> withMemory(initSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "manual-skills" -> withMemory(initManualSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
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
                        BearDoctorTokenUsageRecorder.Snapshot tokenUsage = BearDoctorTokenUsageRecorder.snapshot(internalConversationId);
                        if (consumed.compareAndSet(false, true)
                                && shouldConsumeQuota(signalType, observedContent, tokenUsage)) {
                            long latencyMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
                            consumeQuota(user.getUserId(), safeConversationId, safeAgentType, query,
                                    observedContent.toString(), latencyMillis, tokenUsage, modelSelection.customModelUsed());
                            fillAgentType(internalConversationId, safeAgentType);
                        }
                    } finally {
                        BearDoctorTokenUsageRecorder.clear(internalConversationId);
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
        UserAccount user = user(token);
        String safeAgentType = normalizeAgentType(agentType);
        String safeConversationId = StringUtils.hasText(conversationId) ? conversationId.trim() : "S" + System.currentTimeMillis();
        String internalConversationId = internalConversationId(user.getUserId(), safeConversationId);
        AiSession session = sessionService.saveQuestion(SaveQuestionRequest.builder()
                .sessionId(internalConversationId)
                .question(blank(query))
                .fileid(blank(fileId))
                .tools("")
                .firstResponseTime(Math.max(0L, latencyMillis))
                .build());
        sessionService.updateAnswer(UpdateAnswerRequest.builder()
                .id(session.getId())
                .answer(blank(answer))
                .thinking("平台身份问题使用确定性规则回答，避免底层模型自称模型本体。")
                .tools("")
                .firstResponseTime(Math.max(0L, latencyMillis))
                .totalResponseTime(Math.max(0L, latencyMillis))
                .build());
        fillAgentType(internalConversationId, safeAgentType);
    }

    public FileInfo upload(String token, MultipartFile file, String conversationId) {
        UserAccount user = user(token);
        FileInfo fileInfo = fileManageService.uploadFile(file);
        String ownerConversationId = StringUtils.hasText(conversationId) ? conversationId : "__global";
        fileInfo.setConversationId(internalConversationId(user.getUserId(), ownerConversationId));
        fileInfoService.updateFileInfo(fileInfo);
        return fileInfo;
    }

    public FileInfo getFileInfo(String token, String fileId) {
        UserAccount user = user(token);
        FileInfo fileInfo = fileManageService.getFileInfo(fileId);
        assertOwnedFile(user.getUserId(), fileInfo);
        return fileInfo;
    }

    public String getFileContent(String token, String fileId) {
        getFileInfo(token, fileId);
        return fileManageService.getFileContent(fileId);
    }

    public void deleteFile(String token, String fileId) {
        getFileInfo(token, fileId);
        fileManageService.deleteFile(fileId);
    }

    public List<FileInfo> listFiles(String token) {
        UserAccount user = user(token);
        String prefix = user.getUserId() + ":";
        return fileInfoService.getAllFiles().stream()
                .filter(file -> StringUtils.hasText(file.getConversationId()) && file.getConversationId().startsWith(prefix))
                .toList();
    }

    public boolean fileExists(String token, String fileId) {
        try {
            getFileInfo(token, fileId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stop(String token, String conversationId) {
        UserAccount user = user(token);
        return taskManager.stopTask(internalConversationId(user.getUserId(), conversationId));
    }

    public List<AiSession> querySessions(String token, int pageNum, int pageSize) {
        UserAccount user = user(token);
        String prefix = user.getUserId() + ":";
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePageNum - 1) * safePageSize;
        return aiSessionMapper.selectSessionListWithFirstRecordByPrefix(prefix, offset, safePageSize);
    }

    public long countSessions(String token) {
        UserAccount user = user(token);
        return aiSessionMapper.countSessionByPrefix(user.getUserId() + ":");
    }

    public List<AiSession> querySessionMessages(String token, String conversationId) {
        UserAccount user = user(token);
        String internalConversationId = internalConversationId(user.getUserId(), conversationId);
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .orderByAsc(AiSession::getCreateTime);
        return sessionService.list(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String token, String conversationId) {
        UserAccount user = user(token);
        String internalConversationId = internalConversationId(user.getUserId(), conversationId);
        List<FileInfo> relatedFiles = fileInfoService.getAllFiles().stream()
                .filter(file -> internalConversationId.equals(file.getConversationId()))
                .toList();
        for (FileInfo file : relatedFiles) {
            try {
                fileManageService.deleteFileForSessionCleanup(file.getFileId());
            } catch (Exception e) {
                LOGGER.warn("bear-doctor session file cleanup degraded, fileId={}, reason={}", file.getFileId(), e.getClass().getSimpleName());
                fileInfoService.deleteFileInfo(file.getFileId());
            }
        }
        aiPptInstService.remove(new LambdaQueryWrapper<AiPptInst>()
                .eq(AiPptInst::getConversationId, internalConversationId));
        sessionService.remove(new LambdaQueryWrapper<AiSession>().eq(AiSession::getSessionId, internalConversationId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime rollbackSessionFromMessage(String token, String conversationId, String messageId) {
        UserAccount user = user(token);
        String internalConversationId = internalConversationId(user.getUserId(), conversationId);
        Long recordId = parseRecordId(messageId);
        if (recordId == null) {
            return null;
        }
        AiSession anchor = sessionService.getOne(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .eq(AiSession::getId, recordId)
                .last("LIMIT 1"));
        if (anchor == null || anchor.getCreateTime() == null) {
            return null;
        }
        sessionService.remove(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .ge(AiSession::getCreateTime, anchor.getCreateTime()));
        return anchor.getCreateTime();
    }

    private Long parseRecordId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        try {
            return Long.parseLong(messageId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        String resolvedSkillsDirectory = resolvedSkillsDirectory();
        SkillManager skillManager = manualSkillManager();
        int manualSkillCount = skillManager == null ? 0 : skillManager.getSkillCount();
        List<Map<String, Object>> academicTools = academicToolCallbackFactory == null
                ? List.of()
                : academicToolCallbackFactory.preview("capabilities", true);
        int offlineAcademicToolCount = academicToolCallbackFactory == null
                ? 0
                : academicToolCallbackFactory.preview("capabilities", false).size();
        Map<String, Object> agentAdminStatistics = agentAdminStatistics();
        result.put("chatModelAvailable", chatModelProvider.getIfAvailable() != null);
        result.put("tavilyConfigured", isTavilyConfigured());
        result.put("webSearchAvailable", webSearchToolCallbacks.length > 0);
        result.put("webSearchToolCount", webSearchToolCallbacks.length);
        result.put("webSearchStatus", webSearchStatus());
        result.put("reactorToolEnabled", reactorToolEnabled);
        result.put("academicToolAvailable", !academicTools.isEmpty());
        result.put("academicToolCount", academicTools.size());
        result.put("academicOfflineToolCount", offlineAcademicToolCount);
        result.put("academicTools", academicTools);
        result.put("skillsDirectoryConfigured", StringUtils.hasText(resolvedSkillsDirectory));
        result.put("skillsDirectory", resolvedSkillsDirectory);
        result.put("skillsOutputDirectory", resolvedSkillsOutputDirectory());
        result.put("skillsToolAvailable", skillsToolCallbacks().length > 0);
        result.put("manualSkillsAvailable", manualSkillCount > 0);
        result.put("manualSkillCount", manualSkillCount);
        result.put("manualSkills", manualSkillSummaries(skillManager));
        result.put("manualSkillsEndpoint", "/api/v1/academic/stream");
        result.put("manualSkillsTaskType", "manual-skills");
        result.put("agentAdmin", agentAdminStatistics);
        result.put("agentAdminConfigCount", agentAdminStatistics.getOrDefault("configCount", 0));
        result.put("agentAdminEnabledCount", agentAdminStatistics.getOrDefault("enabledCount", 0));
        result.put("quotaMode", "spring-ai-usage-with-estimated-fallback");
        result.put("apiDocs", "/swagger-ui/index.html");
        List<Map<String, Object>> agentExecutionModes = agentExecutionModes();
        result.put("agentExecutionModes", agentExecutionModes);
        List<Map<String, Object>> workspaceProfiles = workspaceProfiles(academicTools);
        List<Map<String, Object>> toolRuntimeReadiness = toolRuntimeReadiness(academicTools, workspaceProfiles);
        List<Map<String, Object>> toolRuntimeFamilies = toolRuntimeFamilies(toolRuntimeReadiness);
        Map<String, Object> mcpAdminHealth = mcpAdminHealth();
        List<Map<String, Object>> capabilityMatrix =
                capabilityMatrix(academicTools, mcpAdminHealth, agentAdminStatistics,
                        resolvedSkillsDirectory, manualSkillCount);
        result.put("workspaceProfiles", workspaceProfiles);
        result.put("toolRuntimeReadiness", toolRuntimeReadiness);
        result.put("toolRuntimeFamilies", toolRuntimeFamilies);
        result.put("capabilityMatrix", capabilityMatrix);
        result.put("mcpAdminHealth", mcpAdminHealth);
        result.put("toolCatalog", toolCatalog(academicTools, workspaceProfiles));
        result.put("agentPlatformReadiness",
                agentPlatformReadiness(agentExecutionModes, toolRuntimeReadiness, workspaceProfiles, capabilityMatrix));
        return result;
    }

    private Map<String, Object> agentAdminStatistics() {
        if (agentAdminConfigHandler == null) {
            return Map.of(
                    "configCount", 0,
                    "enabledCount", 0,
                    "disabledCount", 0,
                    "categoryCount", 0,
                    "categories", List.of());
        }
        try {
            return agentAdminConfigHandler.statistics();
        } catch (Exception e) {
            LOGGER.warn("agent admin statistics degraded, reason={}", e.getClass().getSimpleName());
            return Map.of(
                    "configCount", 0,
                    "enabledCount", 0,
                    "disabledCount", 0,
                    "categoryCount", 0,
                    "categories", List.of(),
                    "error", e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> mcpAdminHealth() {
        McpAdminHandler handler = mcpAdminHandler == null ? null : mcpAdminHandler.getIfAvailable();
        if (handler == null) {
            return Map.of(
                    "overallStatus", "missing",
                    "serverCount", 0,
                    "enabledServerCount", 0,
                    "readyServerCount", 0,
                    "degradedServerCount", 0,
                    "toolCount", 0,
                    "enabledToolCount", 0,
                    "message", "MCP admin handler is not available");
        }
        try {
            return handler.health();
        } catch (Exception e) {
            LOGGER.warn("mcp admin health degraded, reason={}", e.getClass().getSimpleName());
            return Map.of(
                    "overallStatus", "degraded",
                    "serverCount", 0,
                    "enabledServerCount", 0,
                    "readyServerCount", 0,
                    "degradedServerCount", 0,
                    "toolCount", 0,
                    "enabledToolCount", 0,
                    "error", e.getClass().getSimpleName());
        }
    }

    private List<Map<String, Object>> agentExecutionModes() {
        return List.of(
                agentExecutionMode("chat", "对话助手", "react", "ReAct", "通用问答、交易解释和轻量工具调用"),
                agentExecutionMode("file", "文件问答", "react", "ReAct", "文件理解、引用回答和上下文追问"),
                agentExecutionMode("ppt", "PPT 生成", "flow", "Flow", "需求澄清、大纲、搜索、模板和渲染状态流"),
                agentExecutionMode("deep", "深度任务", "plan-execute", "Plan Execute", "计划拆解、分步执行、反思和动态重规划",
                        true,
                        List.of("plan_update/replan stream event",
                                "AcademicAgentFlowProgress.STATUS_REPLANNED",
                                "AcademicAgentFallbackReplanStrategy default recovery",
                                "planner history versions")),
                agentExecutionMode("image", "图像生成", "react", "ReAct", "图像生成、图生图和多模态参考图处理"),
                agentExecutionMode("data", "数据问答", "react", "ReAct", "数据分析、表格检索和自然语言转 SQL"),
                agentExecutionMode("mrag", "MRAG 知识问答", "react", "ReAct", "多模态检索、知识库证据和资料交叉验证"),
                agentExecutionMode("skills", "技能助手", "skill-sop", "Skill + SOP", "自动选择技能并执行标准流程"),
                agentExecutionMode("manual-skills", "手动技能", "skill-sop", "Skill + SOP", "读取技能文件、检索技能目录和运行技能脚本")
        );
    }

    private Map<String, Object> agentExecutionMode(String agentId,
                                                   String name,
                                                   String family,
                                                   String executionMode,
                                                   String summary) {
        return agentExecutionMode(agentId, name, family, executionMode, summary, false, List.of());
    }

    private Map<String, Object> agentExecutionMode(String agentId,
                                                   String name,
                                                   String family,
                                                   String executionMode,
                                                   String summary,
                                                   boolean replanEnabled,
                                                   List<String> replanEvidence) {
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("agentId", agentId);
        mode.put("name", name);
        mode.put("family", family);
        mode.put("executionMode", executionMode);
        mode.put("summary", summary);
        if (replanEnabled || (replanEvidence != null && !replanEvidence.isEmpty())) {
            mode.put("replanEnabled", replanEnabled);
            mode.put("replanEvidence", replanEvidence == null ? List.of() : replanEvidence);
        }
        return mode;
    }

    private List<Map<String, Object>> manualSkillSummaries(SkillManager skillManager) {
        if (skillManager == null) {
            return List.of();
        }
        return skillManager.getSkills().stream()
                .sorted(Comparator.comparing(SkillMetadata::name))
                .map(this::manualSkillSummary)
                .toList();
    }

    private Map<String, Object> manualSkillSummary(SkillMetadata skill) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", skill.name());
        summary.put("description", skill.description());
        summary.put("descriptionZh", manualSkillDescriptionZh(skill));
        summary.put("source", skill.source() == null ? "" : skill.source().name());
        summary.put("allowedTools", skill.allowedTools() == null ? List.of() : skill.allowedTools());
        summary.put("scriptCount", skill.scripts() == null ? 0 : skill.scripts().size());
        summary.put("scripts", skill.buildScriptSummaries());
        return summary;
    }

    private String manualSkillDescriptionZh(SkillMetadata skill) {
        String description = compactSkillDescription(skill.description());
        return StringUtils.hasText(description) ? description : skill.name();
    }

    private String compactSkillDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }
        return description.replaceAll("\\s+", " ").trim();
    }

    private List<Map<String, Object>> workspaceProfiles(List<Map<String, Object>> academicTools) {
        List<String> toolNames = toolNames(academicTools);
        return List.of(
                workspaceProfile("agent", "/", "chat", "file",
                        List.of("planning", "web_fetch", "deep_search", "code_interpreter", "report_tool"),
                        List.of("answer", "reference", "artifact"), "", "", toolNames),
                workspaceProfile("image", "/workspace/image", "image", "file-or-image",
                        List.of("image_generation", "multimodal_agent", "file_tool"),
                        List.of("image", "prompt", "artifact"),
                        "/api/v1/academic/workspace/image/generate",
                        "/api/v1/academic/workspace/image/history",
                        toolNames),
                workspaceProfile("data", "/workspace/data", "data", "file",
                        List.of("data_analysis", "table_rag", "nl2sql", "report_tool"),
                        List.of("table", "sql", "chart", "report"),
                        "/api/v1/academic/workspace/data/run",
                        "/api/v1/academic/workspace/data/history",
                        toolNames),
                workspaceProfile("mrag", "/workspace/mrag", "mrag", "file-or-image",
                        List.of("multimodal_agent", "file_tool", "table_rag", "deep_search"),
                        List.of("answer", "evidence", "file", "image"),
                        "/api/v1/academic/workspace/mrag/run",
                        "/api/v1/academic/workspace/mrag/history",
                        toolNames),
                workspaceProfile("trade", "/workspace/trade", "data", "none",
                        List.of("planning", "data_analysis", "table_rag", "nl2sql", "report_tool"),
                        List.of("order", "quota", "status", "report"),
                        "/api/v1/academic/stream", "/api/v1/trade/order/my", toolNames)
        );
    }

    private Map<String, Object> workspaceProfile(String id,
                                                 String path,
                                                 String taskType,
                                                 String attachmentMode,
                                                 List<String> primaryTools,
                                                 List<String> outputKinds,
                                                 String runEndpoint,
                                                 String historyEndpoint,
                                                 List<String> availableToolNames) {
        List<String> availableTools = primaryTools.stream()
                .filter(availableToolNames::contains)
                .toList();
        List<String> missingTools = primaryTools.stream()
                .filter(toolName -> !availableToolNames.contains(toolName))
                .toList();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", id);
        profile.put("path", path);
        profile.put("taskType", taskType);
        profile.put("attachmentMode", attachmentMode);
        profile.put("primaryTools", primaryTools);
        profile.put("availableTools", availableTools);
        profile.put("missingTools", missingTools);
        profile.put("outputKinds", outputKinds);
        profile.put("runEndpoint", StringUtils.hasText(runEndpoint) ? runEndpoint.trim() : "");
        profile.put("historyEndpoint", StringUtils.hasText(historyEndpoint) ? historyEndpoint.trim() : "");
        profile.put("status", missingTools.isEmpty() ? "ready" : availableTools.isEmpty() ? "pending" : "degraded");
        return profile;
    }

    private Map<String, Object> toolCatalog(List<Map<String, Object>> academicTools,
                                            List<Map<String, Object>> workspaceProfiles) {
        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        for (Map<String, Object> tool : academicTools == null ? List.<Map<String, Object>>of() : academicTools) {
            String category = String.valueOf(tool.getOrDefault("category", "unknown")).trim();
            String source = String.valueOf(tool.getOrDefault("source", "unknown")).trim();
            byCategory.computeIfAbsent(StringUtils.hasText(category) ? category : "unknown", key -> new ArrayList<>())
                    .add(tool);
            bySource.computeIfAbsent(StringUtils.hasText(source) ? source : "unknown", key -> new ArrayList<>())
                    .add(tool);
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("total", academicTools == null ? 0 : academicTools.size());
        catalog.put("categoryGroups", toolGroups(byCategory));
        catalog.put("sourceGroups", toolGroups(bySource));
        catalog.put("workspaceCoverage", workspaceProfiles.stream()
                .map(profile -> {
                    Map<String, Object> coverage = new LinkedHashMap<>();
                    coverage.put("workspace", profile.get("id"));
                    coverage.put("status", profile.get("status"));
                    coverage.put("runEndpoint", profile.getOrDefault("runEndpoint", ""));
                    coverage.put("historyEndpoint", profile.getOrDefault("historyEndpoint", ""));
                    coverage.put("availableTools", profile.getOrDefault("availableTools", List.of()));
                    coverage.put("missingTools", profile.getOrDefault("missingTools", List.of()));
                    return coverage;
                })
                .toList());
        return catalog;
    }

    private List<Map<String, Object>> toolGroups(Map<String, List<Map<String, Object>>> groups) {
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<String> names = entry.getValue().stream()
                            .map(tool -> String.valueOf(tool.getOrDefault("name", "")).trim())
                            .filter(StringUtils::hasText)
                            .sorted()
                            .toList();
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("key", entry.getKey());
                    group.put("count", names.size());
                    group.put("tools", names);
                    return group;
                })
                .toList();
    }

    private List<Map<String, Object>> capabilityMatrix(List<Map<String, Object>> academicTools,
                                                       Map<String, Object> mcpAdminHealth,
                                                       Map<String, Object> agentAdminStatistics,
                                                       String resolvedSkillsDirectory,
                                                       int manualSkillCount) {
        List<String> toolNames = toolNames(academicTools);
        List<String> implementedTools = AcademicToolOutputNames.orderedRichToolNames();
        List<String> missingRuntimeTools = implementedTools.stream()
                .filter(toolName -> !toolNames.contains(toolName))
                .toList();
        long mcpToolCount = academicTools.stream()
                .filter(tool -> "mcp".equalsIgnoreCase(String.valueOf(tool.getOrDefault("category", ""))))
                .count();
        String mcpOverallStatus = defaultText(mcpAdminHealth.get("overallStatus"), "missing");
        long mcpServerCount = numberValue(mcpAdminHealth.get("serverCount"));
        long mcpEnabledServerCount = numberValue(mcpAdminHealth.get("enabledServerCount"));
        long mcpReadyServerCount = numberValue(mcpAdminHealth.get("readyServerCount"));
        long mcpEnabledToolCount = numberValue(mcpAdminHealth.get("enabledToolCount"));
        boolean mcpReady = "ready".equals(mcpOverallStatus) && mcpEnabledToolCount > 0 && mcpToolCount > 0;

        return List.of(
                capabilityItem("multi-agent", "多智能体协同", "ready",
                        "ReAct、Plan Execute、Flow、动态重规划和会话执行记忆已接入主链路。",
                        List.of("chat/file/skills 使用 ReAct 链路", "deep 使用 Plan Execute", "支持 plan_delta 和 flow_delta"),
                        List.of()),
                capabilityItem("tool-runtime", "工具运行时", "ready",
                        "统一工具注册、结构化输出、产物登记和运行账本已接入。",
                        mergeEvidence(implementedTools, List.of("AcademicToolRuntimeSummary 统计 total/enabled/disabled/category/source")),
                        missingRuntimeTools),
                capabilityItem("mcp", "MCP 管理", mcpReady ? "ready" : "degraded",
                        "支持服务注册、启停、工具发现、缓存和后台配置导入。",
                        List.of("管理接口: /api/v1/mcp/admin/**",
                                "MCP 健康状态: " + mcpOverallStatus,
                                "MCP 服务: " + mcpEnabledServerCount + "/" + mcpServerCount,
                                "MCP 可用服务: " + mcpReadyServerCount + "/" + mcpEnabledServerCount,
                                "MCP 管理启用工具数: " + mcpEnabledToolCount,
                                "已桥接到 Agent 的 MCP 工具数: " + mcpToolCount),
                        mcpGaps(mcpOverallStatus, mcpServerCount, mcpEnabledServerCount, mcpEnabledToolCount, mcpToolCount)),
                capabilityItem("agent-admin", "Agent Admin Config", "ready",
                        "支持 Agent 客户端、API、模型、系统提示词、增强策略、RAG 顺序、工具、MCP 工具和绘图配置。",
                        List.of("/api/v1/agent/admin/configs",
                                "/api/v1/agent/admin/statistics",
                                "/api/v1/agent/admin/assembly",
                                "configCount=" + agentAdminStatistics.getOrDefault("configCount", 0)),
                        List.of()),
                capabilityItem("workspace", "前端工作区", "ready",
                        "前端已提供 Agent、图像生成、数据问答、多模态知识问答和拼团交易工作区。",
                        List.of("/", "/workspace/image", "/workspace/data", "/workspace/mrag", "/workspace/trade"),
                        List.of()),
                capabilityItem("skill-runtime", "Skill 与脚本", StringUtils.hasText(resolvedSkillsDirectory) ? "ready" : "degraded",
                        "支持手动技能读取、技能目录检索、脚本定义和会话产物目录。",
                        List.of("manualSkillCount=" + manualSkillCount,
                                "tools=read_skill/read_skill_file/grep_skill_files/glob_skill_files/list_skill_directory",
                                "skillsDirectory=" + (resolvedSkillsDirectory == null ? "" : resolvedSkillsDirectory)),
                        StringUtils.hasText(resolvedSkillsDirectory) ? List.of() : List.of("未配置 skills.directory")),
                capabilityItem("trade-quota", "交易与额度闭环", "ready",
                        "直接购买、拼团成团、支付状态、额度发放、任务扣减和退款回滚由后端交易系统控制。",
                        List.of("额度扣减模式: spring-ai-usage-with-estimated-fallback",
                                "直购 PAY_SUCCESS 后可发放额度",
                                "拼团 GROUP_SETTLED/DEAL_DONE 后才可发放额度",
                                "前端交易工作区: /workspace/trade"),
                        List.of())
        );
    }

    private List<String> mergeEvidence(List<String> primary, List<String> additional) {
        List<String> evidence = new ArrayList<>();
        if (primary != null) {
            evidence.addAll(primary);
        }
        if (additional != null) {
            evidence.addAll(additional);
        }
        return evidence;
    }

    private List<String> mcpGaps(String overallStatus,
                                 long serverCount,
                                 long enabledServerCount,
                                 long enabledToolCount,
                                 long bridgedToolCount) {
        List<String> gaps = new ArrayList<>();
        if ("missing".equals(overallStatus)) {
            gaps.add("MCP ç®¡ç†å™¨æœªåŠ è½½");
        }
        if (serverCount == 0) {
            gaps.add("è¿˜æ²¡æœ‰æ³¨å†?MCP æœåŠ¡");
        } else if (enabledServerCount == 0) {
            gaps.add("æ²¡æœ‰å¯ç”¨ MCP æœåŠ¡");
        }
        if (enabledServerCount > 0 && enabledToolCount == 0) {
            gaps.add("å½“å‰æ²¡æœ‰å¯ä¾› Agent ä½¿ç”¨çš?MCP å·¥å…·");
        }
        if (bridgedToolCount == 0) {
            gaps.add("å½“å‰æœªå‘çŽ°æˆ–æœªç¼“å­˜å¤–éƒ?MCP å·¥å…·");
        }
        if (StringUtils.hasText(overallStatus)
                && !"ready".equals(overallStatus)
                && !"missing".equals(overallStatus)
                && serverCount > 0) {
            gaps.add("MCP æœåŠ¡å¥åº·çŠ¶æ€ä¸º " + overallStatus);
        }
        return gaps.stream().distinct().toList();
    }

    private List<Map<String, Object>> tradeQuotaSettlementRules() {
        return List.of(
                tradeQuotaSettlementRule("direct-pay-success", "直购支付成功", "PAY_SUCCESS", true,
                        "直购订单支付成功后可以发放额度，但仍以后端支付单和额度流水为准。"),
                tradeQuotaSettlementRule("group-pay-success", "拼团名额已支付", "PAY_SUCCESS", false,
                        "拼团支付成功只表示名额已支付，未成团前不能发放额度。"),
                tradeQuotaSettlementRule("group-settled", "拼团已成团", "GROUP_SETTLED/DEAL_DONE", true,
                        "拼团已成团或交易完成后，才能给同团用户发放额度。"),
                tradeQuotaSettlementRule("refund-success", "退款成功", "REFUND_SUCCESS/REFUNDED", false,
                        "退款或误发时必须记录额度流水并回滚余额。")
        );
    }

    private Map<String, Object> tradeQuotaSettlementRule(String key,
                                                         String scenario,
                                                         String requiredState,
                                                         boolean quotaGrantAllowed,
                                                         String operatorHint) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("key", key);
        rule.put("scenario", scenario);
        rule.put("requiredState", requiredState);
        rule.put("quotaGrantAllowed", quotaGrantAllowed);
        rule.put("operatorHint", operatorHint);
        return rule;
    }

    private List<Map<String, Object>> toolRuntimeReadiness(List<Map<String, Object>> academicTools,
                                                           List<Map<String, Object>> workspaceProfiles) {
        List<String> availableToolNames = toolNames(academicTools);
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> tool : academicTools == null ? List.<Map<String, Object>>of() : academicTools) {
            String name = String.valueOf(tool.getOrDefault("name", "")).trim();
            if (StringUtils.hasText(name)) {
                byName.putIfAbsent(name, tool);
            }
        }
        return AcademicToolOutputNames.orderedRichToolNames().stream()
                .map(toolName -> {
                    boolean ready = availableToolNames.contains(toolName);
                    Map<String, Object> source = byName.getOrDefault(toolName, Map.of());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", toolName);
                    item.put("status", ready ? "ready" : "missing");
                    item.put("category", String.valueOf(source.getOrDefault("category", categoryForTool(toolName))));
                    item.put("source", String.valueOf(source.getOrDefault("source", ready ? "runtime" : "port")));
                    item.put("requiredArguments", source.getOrDefault("requiredArguments", List.of()));
                    item.put("inputFields", source.getOrDefault("inputFields", fallbackInputFields(toolName)));
                    item.put("outputKinds", outputKindsForTool(toolName));
                    item.put("workspaces", workspacesUsingTool(toolName, workspaceProfiles));
                    item.put("message", ready ? "registered" : "external port is not configured");
                    item.put("hint", ready ? "" : toolRuntimeHint(toolName));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> toolRuntimeFamilies(List<Map<String, Object>> toolRuntimeReadiness) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> item : toolRuntimeReadiness == null ? List.<Map<String, Object>>of() : toolRuntimeReadiness) {
            String name = text(item.get("name"));
            if (StringUtils.hasText(name)) {
                byName.put(name, item);
            }
        }
        return List.of(
                toolRuntimeFamily("web", "网页抓取", List.of(AcademicToolOutputNames.WEB_FETCH, AcademicToolOutputNames.DEEP_SEARCH), byName),
                toolRuntimeFamily("data", "数据分析", List.of(AcademicToolOutputNames.DATA_ANALYSIS, AcademicToolOutputNames.TABLE_RAG, AcademicToolOutputNames.NL2SQL), byName),
                toolRuntimeFamily("image", "图像生成", List.of(AcademicToolOutputNames.IMAGE_GENERATION), byName),
                toolRuntimeFamily("report", "报告工具", List.of(AcademicToolOutputNames.REPORT_TOOL), byName),
                toolRuntimeFamily("code", "代码解释器", List.of(AcademicToolOutputNames.CODE_INTERPRETER, AcademicToolOutputNames.SCRIPT_RUNNER), byName),
                toolRuntimeFamily("multimodal", "多模态", List.of(AcademicToolOutputNames.MULTIMODAL_AGENT, AcademicToolOutputNames.FILE_TOOL), byName)
        );
    }

    private Map<String, Object> toolRuntimeFamily(String key,
                                                  String label,
                                                  List<String> tools,
                                                  Map<String, Map<String, Object>> byName) {
        List<String> readyTools = tools.stream()
                .filter(toolName -> "ready".equals(text(byName.getOrDefault(toolName, Map.of()).get("status"))))
                .toList();
        List<String> missingTools = tools.stream()
                .filter(toolName -> !readyTools.contains(toolName))
                .toList();
        List<String> outputKinds = tools.stream()
                .flatMap(toolName -> stringValues(byName.getOrDefault(toolName, Map.of()).get("outputKinds")).stream())
                .distinct()
                .limit(5)
                .toList();
        List<String> workspaces = tools.stream()
                .flatMap(toolName -> stringValues(byName.getOrDefault(toolName, Map.of()).get("workspaces")).stream())
                .distinct()
                .limit(5)
                .toList();
        String status = readyTools.isEmpty() ? "missing" : missingTools.isEmpty() ? "ready" : "partial";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", status);
        item.put("statusLabel", toolRuntimeFamilyStatusLabel(status));
        item.put("readyCount", readyTools.size());
        item.put("totalCount", tools.size());
        item.put("tools", tools);
        item.put("missingTools", missingTools);
        item.put("outputKinds", outputKinds);
        item.put("workspaces", workspaces);
        item.put("action", missingTools.isEmpty() ? "核心工具已覆盖" : "补齐 " + compactLabels(missingTools, 2) + " 工具运行时");
        return item;
    }

    private String toolRuntimeFamilyStatusLabel(String status) {
        return switch (status) {
            case "ready" -> "已就绪";
            case "partial" -> "部分就绪";
            default -> "未就绪";
        };
    }

    private Map<String, Object> agentPlatformReadiness(List<Map<String, Object>> executionModes,
                                                       List<Map<String, Object>> toolRuntimeReadiness,
                                                       List<Map<String, Object>> workspaceProfiles,
                                                       List<Map<String, Object>> capabilityMatrix) {
        List<String> requiredFamilies = List.of("react", "plan-execute", "flow", "skill-sop");
        List<String> coveredFamilies = executionModes.stream()
                .map(mode -> text(mode.get("family")))
                .filter(requiredFamilies::contains)
                .distinct()
                .toList();
        List<String> missingFamilies = requiredFamilies.stream()
                .filter(family -> !coveredFamilies.contains(family))
                .toList();
        long replanModeCount = executionModes.stream()
                .filter(mode -> truthy(mode.get("dynamicReplan")))
                .count();
        List<String> orderedTools = AcademicToolOutputNames.orderedRichToolNames();
        List<String> readyTools = toolRuntimeReadiness.stream()
                .filter(item -> "ready".equals(text(item.get("status"))))
                .map(item -> text(item.get("name")))
                .filter(orderedTools::contains)
                .distinct()
                .toList();
        List<String> missingTools = orderedTools.stream()
                .filter(tool -> !readyTools.contains(tool))
                .toList();
        List<String> requiredWorkspaces = List.of("agent", "image", "data", "mrag", "trade");
        List<String> coveredWorkspaces = workspaceProfiles.stream()
                .filter(this::workspaceEntryReady)
                .map(profile -> text(profile.get("id")))
                .filter(requiredWorkspaces::contains)
                .distinct()
                .toList();
        List<String> missingWorkspaces = requiredWorkspaces.stream()
                .filter(workspace -> !coveredWorkspaces.contains(workspace))
                .toList();
        Map<String, Object> mcp = capabilityByKey(capabilityMatrix, "mcp");
        List<String> mcpGaps = mcp.isEmpty() ? List.of("MCP 管理能力未接入") : stringValues(mcp.get("gaps"));
        String mcpStatus = defaultText(mcp.get("status"), mcpGaps.isEmpty() ? "ready" : "degraded");
        Map<String, Object> tradeQuota = capabilityByKey(capabilityMatrix, "trade-quota");
        List<Map<String, Object>> settlementRules = mapList(tradeQuota.get("settlementRules"));
        long blockedSettlementRuleCount = settlementRules.stream()
                .filter(rule -> !truthy(rule.get("quotaGrantAllowed")))
                .count();
        List<String> guardrails = stringValues(tradeQuota.get("guardrails"));
        boolean ready = missingFamilies.isEmpty()
                && replanModeCount > 0
                && missingTools.isEmpty()
                && missingWorkspaces.isEmpty()
                && mcpGaps.isEmpty()
                && !settlementRules.isEmpty()
                && blockedSettlementRuleCount > 0;
        String status = executionModes.isEmpty() ? "missing" : ready ? "ready" : "partial";
        List<String> gaps = new ArrayList<>();
        if (!missingFamilies.isEmpty()) {
            gaps.add("缺少执行族：" + String.join("、", missingFamilies));
        }
        if (replanModeCount == 0) {
            gaps.add("缺少动态重规划证据");
        }
        if (!missingTools.isEmpty()) {
            gaps.add("工具运行时未全部就绪：" + compactLabels(missingTools, 4));
        }
        if (!missingWorkspaces.isEmpty()) {
            gaps.add("工作区入口未完整：" + String.join("、", missingWorkspaces));
        }
        gaps.addAll(mcpGaps);
        if (settlementRules.isEmpty()) {
            gaps.add("缺少拼团额度发放规则");
        }
        List<String> actions = new ArrayList<>();
        if (!missingTools.isEmpty()) {
            actions.add("启动或配置工具运行时：" + compactLabels(missingTools, 3));
        }
        if (!mcpGaps.isEmpty()) {
            actions.add("注册、发现并缓存 MCP 工具");
        }
        if (replanModeCount == 0 || !missingFamilies.isEmpty()) {
            actions.add("补齐多智能体执行模式与重规划证据");
        }
        if (settlementRules.isEmpty()) {
            actions.add("补齐拼团额度发放规则");
        }
        if (ready) {
            actions.add("Agent 与拼团交易闭环已具备完整演示面");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("statusLabel", platformReadinessStatusLabel(status));
        result.put("title", "Agent + 拼团交易系统就绪度");
        result.put("metrics", List.of(
                readinessMetric("families", "执行族", coveredFamilies.size() + "/" + requiredFamilies.size(), missingFamilies.isEmpty() ? "good" : "warn"),
                readinessMetric("replan", "重规划", String.valueOf(replanModeCount), replanModeCount > 0 ? "good" : "warn"),
                readinessMetric("tools", "工具", readyTools.size() + "/" + orderedTools.size(), missingTools.isEmpty() ? "good" : "warn"),
                readinessMetric("workspaces", "工作区", coveredWorkspaces.size() + "/" + requiredWorkspaces.size(), missingWorkspaces.isEmpty() ? "good" : "warn"),
                readinessMetric("tradeRules", "交易规则", String.valueOf(settlementRules.size()), settlementRules.isEmpty() ? "warn" : "good")
        ));
        result.put("coveredFamilies", coveredFamilies);
        result.put("missingFamilies", missingFamilies);
        result.put("replanModeCount", replanModeCount);
        result.put("readyToolCount", readyTools.size());
        result.put("requiredToolCount", orderedTools.size());
        result.put("missingTools", missingTools);
        result.put("coveredWorkspaces", coveredWorkspaces);
        result.put("missingWorkspaces", missingWorkspaces);
        result.put("mcpStatus", mcpStatus);
        result.put("mcpGaps", mcpGaps);
        result.put("settlementRuleCount", settlementRules.size());
        result.put("blockedSettlementRuleCount", blockedSettlementRuleCount);
        result.put("tradeGuardrails", guardrails);
        result.put("gaps", gaps);
        result.put("actions", actions);
        return result;
    }

    private List<String> fallbackInputFields(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> List.of("url", "query");
            case AcademicToolOutputNames.DATA_ANALYSIS -> List.of("task", "rows", "columns");
            case AcademicToolOutputNames.REPORT_TOOL -> List.of("title", "content", "format");
            case AcademicToolOutputNames.PLANNING -> List.of("goal", "context");
            case AcademicToolOutputNames.CODE_INTERPRETER -> List.of("task", "language", "code");
            case AcademicToolOutputNames.IMAGE_GENERATION -> List.of("prompt", "model", "quality", "aspectRatio", "size", "mode");
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> List.of("question", "imageUrls", "fileIds");
            case AcademicToolOutputNames.DEEP_SEARCH -> List.of("query", "scope");
            case AcademicToolOutputNames.FILE_TOOL -> List.of("action", "fileId", "fileName");
            case AcademicToolOutputNames.SCRIPT_RUNNER -> List.of("scriptName", "runtime", "arguments");
            case AcademicToolOutputNames.TABLE_RAG -> List.of("query", "modelCodeList");
            case AcademicToolOutputNames.NL2SQL -> List.of("query", "schemaInfo");
            default -> List.of();
        };
    }

    private List<String> outputKindsForTool(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> List.of("web", "file");
            case AcademicToolOutputNames.DATA_ANALYSIS -> List.of("table", "summary");
            case AcademicToolOutputNames.REPORT_TOOL -> List.of("report", "artifact");
            case AcademicToolOutputNames.PLANNING -> List.of("plan", "flow");
            case AcademicToolOutputNames.CODE_INTERPRETER -> List.of("code", "file", "summary");
            case AcademicToolOutputNames.IMAGE_GENERATION -> List.of("image", "artifact");
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> List.of("multimodal", "evidence");
            case AcademicToolOutputNames.DEEP_SEARCH -> List.of("answer", "reference");
            case AcademicToolOutputNames.FILE_TOOL -> List.of("file", "content");
            case AcademicToolOutputNames.SCRIPT_RUNNER -> List.of("script", "artifact");
            case AcademicToolOutputNames.TABLE_RAG -> List.of("schema", "evidence");
            case AcademicToolOutputNames.NL2SQL -> List.of("sql", "table");
            default -> List.of("result");
        };
    }

    private List<String> workspacesUsingTool(String toolName, List<Map<String, Object>> workspaceProfiles) {
        if (!StringUtils.hasText(toolName) || workspaceProfiles == null) {
            return List.of();
        }
        return workspaceProfiles.stream()
                .filter(profile -> profile.get("primaryTools") instanceof List<?> tools
                        && tools.stream().map(String::valueOf).anyMatch(toolName::equals))
                .map(profile -> String.valueOf(profile.getOrDefault("id", "")).trim())
                .filter(StringUtils::hasText)
                .toList();
    }

    private String toolRuntimeHint(String toolName) {
        String base = "Start tools/reactor-tool/start.ps1 and set AGENT_GROUP_REACTOR_TOOL_ENABLED=true, AGENT_GROUP_REACTOR_TOOL_BASE_URL=http://127.0.0.1:1801.";
        return switch (toolName) {
            case AcademicToolOutputNames.IMAGE_GENERATION -> base + " Image generation also requires model and storage config in reactor-tool .env.";
            case AcademicToolOutputNames.DATA_ANALYSIS,
                 AcademicToolOutputNames.TABLE_RAG,
                 AcademicToolOutputNames.NL2SQL -> base + " Data tools require table, database, or vector retrieval config.";
            case AcademicToolOutputNames.DEEP_SEARCH,
                 AcademicToolOutputNames.WEB_FETCH -> base + " Search tools require an external search provider; direct search is used as fallback.";
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> base + " Multimodal tools require file parsing, table understanding, and image understanding capability.";
            case AcademicToolOutputNames.SCRIPT_RUNNER,
                 AcademicToolOutputNames.CODE_INTERPRETER -> base + " Script tools require a local Python/code execution runtime.";
            default -> base;
        };
    }
    private String categoryForTool(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> "web";
            case AcademicToolOutputNames.DEEP_SEARCH -> "search";
            case AcademicToolOutputNames.DATA_ANALYSIS,
                 AcademicToolOutputNames.TABLE_RAG,
                 AcademicToolOutputNames.NL2SQL -> "data";
            case AcademicToolOutputNames.REPORT_TOOL -> "report";
            case AcademicToolOutputNames.PLANNING -> "planning";
            case AcademicToolOutputNames.CODE_INTERPRETER -> "code";
            case AcademicToolOutputNames.IMAGE_GENERATION -> "image";
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> "multimodal";
            case AcademicToolOutputNames.FILE_TOOL -> "file";
            case AcademicToolOutputNames.SCRIPT_RUNNER -> "skill";
            default -> "tool";
        };
    }

    private Map<String, Object> capabilityItem(String key,
                                               String label,
                                               String status,
                                               String summary,
                                               List<String> evidence,
                                               List<String> gaps) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", status);
        item.put("summary", summary);
        item.put("evidence", evidence == null ? List.of() : evidence);
        item.put("gaps", gaps == null ? List.of() : gaps);
        if ("multi-agent".equals(key)) {
            Map<String, Object> dynamicReplan = new LinkedHashMap<>();
            dynamicReplan.put("enabled", true);
            dynamicReplan.put("executionModes", List.of("deep"));
            dynamicReplan.put("streamEvents", List.of("plan_delta:replan", "flow_delta:REPLANNED"));
            dynamicReplan.put("historyEvidence", List.of("AcademicReplayProjector", "planner history versions", "AcademicAgentFallbackReplanStrategy"));
            item.put("dynamicReplan", dynamicReplan);
        }
        if ("trade-quota".equals(key)) {
            item.put("authoritativeSources", List.of("trade_order", "pay_order", "group_buy_team", "quota_flow"));
            item.put("userAgentExposure", "backend_only");
            item.put("settlementRules", tradeQuotaSettlementRules());
            item.put("guardrails", List.of(
                    "前端和 Agent 不能直接决定额度到账",
                    "拼团支付成功不等于额度到账",
                    "高风险状态必须来自后端交易系统"));
        }
        if ("tool-runtime".equals(key)) {
            List<String> implementedTools = evidence == null ? List.of() : evidence;
            List<String> missingRuntimeTools = gaps == null ? List.of() : gaps;
            item.put("implementedTools", implementedTools);
            item.put("runtimeEnabledTools", implementedTools.stream()
                    .filter(toolName -> !missingRuntimeTools.contains(toolName))
                    .toList());
            item.put("missingRuntimeTools", missingRuntimeTools);
            item.put("gaps", List.of());
        }
        return item;
    }

    private List<String> toolNames(List<Map<String, Object>> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> String.valueOf(tool.getOrDefault("name", "")).trim())
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> capabilityByKey(List<Map<String, Object>> capabilityMatrix, String key) {
        if (capabilityMatrix == null || !StringUtils.hasText(key)) {
            return Map.of();
        }
        return capabilityMatrix.stream()
                .filter(item -> key.equals(text(item.get("key"))))
                .findFirst()
                .orElse(Map.of());
    }

    private boolean workspaceEntryReady(Map<String, Object> profile) {
        if (profile == null) {
            return false;
        }
        String id = text(profile.get("id"));
        return StringUtils.hasText(text(profile.get("runEndpoint")))
                || ("agent".equals(id) && "/".equals(text(profile.get("path"))));
    }

    private List<String> stringValues(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> source) {
                Map<String, Object> map = new LinkedHashMap<>();
                source.forEach((key, val) -> map.put(String.valueOf(key), val));
                result.add(map);
            }
        }
        return result;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value).toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private String compactLabels(List<String> values, int limit) {
        List<String> cleanValues = values == null
                ? List.of()
                : values.stream().filter(StringUtils::hasText).distinct().toList();
        if (cleanValues.isEmpty()) {
            return "";
        }
        int safeLimit = Math.max(1, limit);
        List<String> visible = cleanValues.stream().limit(safeLimit).toList();
        int more = Math.max(0, cleanValues.size() - visible.size());
        return String.join("、", visible) + (more > 0 ? " 等" + more + "项" : "");
    }

    private Map<String, Object> readinessMetric(String key, String label, String value, String tone) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("key", key);
        metric.put("label", label);
        metric.put("value", value);
        metric.put("tone", tone);
        return metric;
    }

    private String platformReadinessStatusLabel(String status) {
        return switch (status) {
            case "ready" -> "ready";
            case "missing" -> "missing config";
            default -> "checking";
        };
    }
    public String externalConversationId(String userId, String sessionId) {
        String prefix = userId + ":";
        return sessionId != null && sessionId.startsWith(prefix) ? sessionId.substring(prefix.length()) : sessionId;
    }

    private void initWebSearchToolCallbacks() {
        ToolCallback[] fallbackSearchTools = SearchTool.create(externalSearchService);
        if (!ApiKeyValidator.isValidApiKey(tavilyApiKey) || !StringUtils.hasText(tavilyMcpUrl)) {
            LOGGER.warn("bear-doctor tavily tool init skipped, reason=missing_config");
            webSearchToolCallbacks = fallbackSearchTools;
            webSearchStatus = fallbackSearchTools.length > 0 ? "direct-api" : "missing-config";
            return;
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().header("Authorization", "Bearer " + tavilyApiKey);
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(tavilyMcpUrl)
                    .requestBuilder(requestBuilder)
                    .build();
            McpSyncClient tavilyMcp = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(120))
                    .build();
            tavilyMcp.initialize();
            SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(List.of(tavilyMcp));
            webSearchToolCallbacks = ToolMergeUtils.mergeTools(provider.getToolCallbacks(), fallbackSearchTools);
            webSearchStatus = fallbackSearchTools.length > 0 ? "mcp-and-direct-api" : "mcp";
        } catch (Exception e) {
            LOGGER.warn("bear-doctor tavily tool init failed, reason={}", e.getClass().getSimpleName());
            webSearchToolCallbacks = fallbackSearchTools;
            webSearchStatus = fallbackSearchTools.length > 0 ? "direct-api-fallback" : "configured-but-no-tools";
        }
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
                .build();
    }

    private PPTBuilderAgent initPPTBuilderAgent(String userId, String conversationId, ChatModel chatModel,
                                                ToolCallback[] searchTools,
                                                boolean webSearchEnabled,
                                                String executionMemoryPrompt) {
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                academicToolCallbacks("ppt", userId, conversationId, webSearchEnabled),
                skillsToolCallbacks()
        );
        return new PPTBuilderAgent(chatModel, Arrays.asList(tools), executionMemoryPrompt, sessionService, taskManager);
    }

    private PlanExecuteAgent initPlanExecuteAgent(String userId, String conversationId, ChatModel chatModel,
                                                  ToolCallback[] searchTools,
                                                  boolean webSearchEnabled,
                                                  String executionMemoryPrompt) {
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                academicToolCallbacks("deep", userId, conversationId, webSearchEnabled)
        );
        return PlanExecuteAgent.builder()
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(executionMemoryPrompt)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(3)
                .build();
    }

    private SkillsReactAgent initSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                  ToolCallback[] searchTools,
                                                  boolean webSearchEnabled,
                                                  String executionMemoryPrompt) {
        String outputDirectory = sessionSkillsOutputDirectory(conversationId);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                academicToolCallbacks("skills", userId, conversationId, webSearchEnabled),
                skillsToolCallbacks(),
                SkillRuntimeTools.create(resolvedSkillsDirectory(), projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name("skills")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(skillRuntimePrompt(outputDirectory, webSearchEnabled), executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .contextPolicy(ContextPolicy.defaults())
                .build();
    }

    private SkillsReactAgent initWorkspaceReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                     ToolCallback[] searchTools,
                                                     boolean webSearchEnabled,
                                                     String workspace,
                                                     String executionMemoryPrompt) {
        String outputDirectory = sessionSkillsOutputDirectory(conversationId);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                academicToolCallbacks(workspace, userId, conversationId, webSearchEnabled),
                skillsToolCallbacks(),
                SkillRuntimeTools.create(resolvedSkillsDirectory(), projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name(workspace + "-workspace")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(joinPrompts(workspaceRuntimePrompt(outputDirectory, webSearchEnabled, workspace),
                        executionMemoryPrompt))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(8)
                .contextPolicy(ContextPolicy.defaults())
                .build();
    }

    private SkillsReactAgent initManualSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                        ToolCallback[] searchTools,
                                                        boolean webSearchEnabled,
                                                        String executionMemoryPrompt) {
        String outputDirectory = sessionSkillsOutputDirectory(conversationId);
        SkillManager skillManager = manualSkillManager();
        ToolCallback[] manualSkillTools = manualSkillToolCallbacks(skillManager);
        String skillsPrompt = skillManager == null ? "" : skillManager.formatPrompt();
        String runtimePrompt = skillRuntimePrompt(outputDirectory, webSearchEnabled);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                academicToolCallbacks("manual-skills", userId, conversationId, webSearchEnabled),
                manualSkillTools,
                SkillRuntimeTools.create(resolvedSkillsDirectory(), projectRoot().toString(), outputDirectory)
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
                .build();
    }

    private ToolCallback[] manualSkillToolCallbacks(SkillManager skillManager) {
        if (skillManager == null) {
            return new ToolCallback[0];
        }
        var registry = skillManager.getRegistry();
        return new ToolCallback[]{
                ReadSkillTool.create(registry),
                ReadSkillFileTool.create(registry),
                GrepSkillFileTool.create(registry),
                GlobSkillFileTool.create(registry),
                ListSkillDirectoryTool.create(registry)
        };
    }

    private ToolCallback[] fileContentToolCallbacks(String userId) {
        return ToolCallbacks.from(new OwnedFileContentTool(userId));
    }

    private ToolCallback[] academicToolCallbacks(String scene,
                                                 String userId,
                                                 String conversationId,
                                                 boolean webAccessEnabled) {
        if (academicToolCallbackFactory == null) {
            return new ToolCallback[0];
        }
        try {
            return academicToolCallbackFactory.create(scene, userId, conversationId, webAccessEnabled);
        } catch (Exception e) {
            LOGGER.warn("bear-doctor academic tool init skipped, scene={}, reason={}",
                    scene, e.getClass().getSimpleName());
            return new ToolCallback[0];
        }
    }

    private ToolCallback[] skillsToolCallbacks() {
        String directory = resolvedSkillsDirectory();
        if (!StringUtils.hasText(directory)) {
            return new ToolCallback[0];
        }
        try {
            return new ToolCallback[]{SkillsTool.builder().addSkillsDirectory(directory).build()};
        } catch (IllegalArgumentException e) {
            LOGGER.warn("bear-doctor skills tool init skipped, reason={}", e.getMessage());
            return new ToolCallback[0];
        }
    }

    private SkillManager manualSkillManager() {
        String directory = resolvedSkillsDirectory();
        if (!StringUtils.hasText(directory)) {
            return null;
        }
        try {
            SkillConfig skillConfig = SkillConfig.builder()
                    .addDirectory(directory)
                    .build();
            return SkillManager.create(skillConfig);
        } catch (Exception e) {
            LOGGER.warn("bear-doctor manual skills init skipped, reason={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String resolvedSkillsDirectory() {
        String configured = StringUtils.hasText(skillsDirectory) ? skillsDirectory.trim() : "skills";
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        addSkillsDirectoryCandidate(candidates, Path.of(configured));
        addSkillsDirectoryCandidate(candidates, cwd.resolve(configured));
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            addSkillsDirectoryCandidate(candidates, cursor.resolve(configured));
            addSkillsDirectoryCandidate(candidates, cursor.resolve("skills"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return configured;
    }

    private void addSkillsDirectoryCandidate(List<Path> candidates, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private String resolvedSkillsOutputDirectory() {
        String configured = StringUtils.hasText(skillsOutputDirectory) ? skillsOutputDirectory.trim() : "outputs";
        Path outputPath = Path.of(configured);
        if (!outputPath.isAbsolute()) {
            Path projectRoot = projectRoot();
            outputPath = projectRoot.resolve(configured).normalize();
        }
        try {
            Files.createDirectories(outputPath);
        } catch (Exception e) {
            LOGGER.warn("bear-doctor skills output directory create failed, path={}, reason={}",
                    outputPath, e.getClass().getSimpleName());
        }
        return outputPath.toAbsolutePath().normalize().toString();
    }

    private String sessionSkillsOutputDirectory(String conversationId) {
        Path outputPath = Path.of(resolvedSkillsOutputDirectory())
                .resolve("session_" + encode(conversationId))
                .normalize();
        try {
            Files.createDirectories(outputPath);
            SkillRuntimeTools.prepareSessionOutput(resolvedSkillsDirectory(), projectRoot().toString(), outputPath.toString());
        } catch (Exception e) {
            LOGGER.warn("bear-doctor session output directory prepare failed, path={}, reason={}",
                    outputPath, e.getClass().getSimpleName());
        }
        return outputPath.toAbsolutePath().normalize().toString();
    }

    private Path projectRoot() {
        String resolvedSkillsDirectory = resolvedSkillsDirectory();
        if (StringUtils.hasText(resolvedSkillsDirectory)) {
            Path skillsPath = Path.of(resolvedSkillsDirectory).toAbsolutePath().normalize();
            if (Files.isDirectory(skillsPath) && skillsPath.getParent() != null) {
                return skillsPath.getParent();
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if ("backend".equalsIgnoreCase(cwd.getFileName() == null ? "" : cwd.getFileName().toString())
                && cwd.getParent() != null) {
            return cwd.getParent();
        }
        if ("agent-group-app".equalsIgnoreCase(cwd.getFileName() == null ? "" : cwd.getFileName().toString())
                && cwd.getParent() != null
                && cwd.getParent().getParent() != null) {
            return cwd.getParent().getParent();
        }
        return cwd;
    }

    private String skillRuntimePrompt(String outputDirectory, boolean webSearchEnabled) {
        String webSearchRule = webSearchEnabled
                ? "- Web search is enabled. Use tools for facts, citations, or fresh information."
                : "- Web search is disabled. Do not call search tools; ask for source material when needed.";
        return """
                ## Skill runtime rules
                - Output directory: %s
                - Write generated files into the output directory and mention file names in the final answer.
                %s
                - Use registered tools for script execution; do not invent execution results.
                - For reports, tables, PPT, or images, clarify the target before calling tools.
                - If a tool fails, report the reason and provide a workable fallback.
                """.formatted(outputDirectory, webSearchRule);
    }
    private String workspaceRuntimePrompt(String outputDirectory, boolean webSearchEnabled, String workspace) {
        String base = skillRuntimePrompt(outputDirectory, webSearchEnabled);
        if ("image".equals(workspace)) {
            return ("""
                    ## Image workspace
                    - Prefer image_generation for image requests and keep prompt, size, and artifact links.
                    - Use planning first when the request needs multi-step design.
                    """ + base).trim();
        }
        if ("data".equals(workspace)) {
            return ("""
                    ## Data workspace
                    - Prefer data_analysis, table_rag, or nl2sql for tables, databases, and structured questions.
                    - Include data scope, key findings, and reproducible query or analysis steps.
                    """ + base).trim();
        }
        if ("mrag".equals(workspace)) {
            return ("""
                    ## Multimodal retrieval workspace
                    - Combine file_tool, multimodal_agent, table_rag, deep_search, and web_fetch when needed.
                    - Separate file evidence, search evidence, and model inference.
                    """ + base).trim();
        }
        if ("trade".equals(workspace)) {
            return ("""
                    ## Trade data workspace
                    - Trade tasks only explain records, status, and exceptions; quota settlement is not an Agent capability.
                    - Separate group-payment success from quota arrival; unsettled groups must not be shown as credited.
                    """ + base).trim();
        }
        return base;
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
        if ("mrag".equals(workspace)) {
            return """
                    Handle this as a multimodal retrieval workspace request. Combine file, table, image, and retrieval evidence.
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
                ## è”ç½‘æœç´¢çŠ¶æ€?                æœ¬è½®æœªå¼€å¯è”ç½‘æœç´¢ã€‚ä¸è¦è°ƒç”¨æœç´¢å·¥å…·ï¼Œä¹Ÿä¸è¦è¾“å‡?ToolCall/search æ–‡æœ¬ã€?                æ™®é€šçŸ¥è¯†å’Œä¸Šä¸‹æ–‡è¶³å¤Ÿæ—¶ç›´æŽ¥å›žç­”ï¼›ç¡®å®žéœ€è¦å®žæ—¶ä¿¡æ¯æ—¶ï¼Œè¯·æç¤ºç”¨æˆ·å¼€å¯è”ç½‘æœç´¢åŽé‡è¯•ã€?                """;
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

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
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
            return new RuntimeModelSelection(new UsageRecordingChatModel(chatModel, conversationId), true);
        }
        if (hasCustomModelConfig(llmBaseUrl, llmApiKey, llmModel)) {
            ChatModel chatModel = customChatModel(llmBaseUrl, llmApiKey, llmModel);
            return new RuntimeModelSelection(new UsageRecordingChatModel(chatModel, conversationId), true);
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AppException("AGENT_0007", "Chat model is not configured.");
        }
        return new RuntimeModelSelection(new UsageRecordingChatModel(chatModel, conversationId), false);
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
        return userAccountService.requireUserByToken(token);
    }

    private void validateFileAccess(String userId, String internalConversationId, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        for (String currentFileId : splitFileIds(fileId)) {
            FileInfo fileInfo = fileManageService.getFileInfo(currentFileId);
            assertOwnedFile(userId, fileInfo);
        }
    }

    private List<String> splitFileIds(String fileIds) {
        if (!StringUtils.hasText(fileIds)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String fileId : fileIds.split("[,ï¼Œ\\s]+")) {
            String trimmed = fileId == null ? "" : fileId.trim();
            if (StringUtils.hasText(trimmed) && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private void assertOwnedFile(String userId, FileInfo fileInfo) {
        String prefix = userId + ":";
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getConversationId())
                || !fileInfo.getConversationId().startsWith(prefix)) {
            throw new AppException("FILE_0001", "æ–‡ä»¶ä¸å­˜åœ¨æˆ–æ— æƒè®¿é—®");
        }
    }

    private void consumeQuota(String userId,
                              String conversationId,
                              String agentType,
                              String query,
                              String observedContent,
                              long latencyMillis,
                              BearDoctorTokenUsageRecorder.Snapshot tokenUsage,
                              boolean customModelUsed) {
        TokenUsageMetrics usage = hasRealUsage(tokenUsage)
                ? new TokenUsageMetrics(tokenUsage.promptTokens(), tokenUsage.completionTokens(),
                tokenUsage.totalTokens(), BigDecimal.ZERO)
                : estimateTokenUsage(query, observedContent);
        String model = hasRealUsage(tokenUsage) && StringUtils.hasText(tokenUsage.model())
                ? tokenUsage.model()
                : "agent-workspace-estimated";
        userQuotaService.consumeForAcademicTask(userId, conversationId, agentType,
                usage, model, latencyMillis, customModelUsed);
    }

    private boolean hasRealUsage(BearDoctorTokenUsageRecorder.Snapshot tokenUsage) {
        return tokenUsage != null && tokenUsage.hasUsage();
    }

    private boolean shouldConsumeQuota(SignalType signalType,
                                       StringBuilder observedContent,
                                       BearDoctorTokenUsageRecorder.Snapshot tokenUsage) {
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
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, internalConversationId)
                .isNull(AiSession::getAgentType);
        AiSession update = new AiSession();
        update.setAgentType(agentType);
        sessionService.update(update, wrapper);
    }

    private String internalConversationId(String userId, String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new AppException("0001", "ä¼šè¯ç¼–å·ä¸èƒ½ä¸ºç©º");
        }
        return userId + ":" + conversationId.trim();
    }

    private String normalizeAgentType(String agentType) {
        String type = StringUtils.hasText(agentType) ? agentType.trim().toLowerCase() : "chat";
        return switch (type) {
            case "file", "paper" -> "file";
            case "ppt", "pptx" -> "ppt";
            case "deep", "deep-research" -> "deep";
            case "image", "image-generation", "workspace-image" -> "image";
            case "data", "data-qa", "workspace-data", "nl2sql", "table-rag",
                 "trade", "trade-flow", "group-trade", "workspace-trade" -> "data";
            case "mrag", "multi-modal-rag", "multimodal-rag", "workspace-mrag" -> "mrag";
            case "skills" -> "skills";
            case "manual", "manual-skills", "skills-manual" -> "manual-skills";
            default -> "chat";
        };
    }

    private boolean isTavilyConfigured() {
        return ApiKeyValidator.isValidApiKey(tavilyApiKey);
    }

    private String webSearchStatus() {
        return webSearchStatus;
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
                LOGGER.warn("bear-doctor file tool access denied or failed, fileId={}, reason={}", fileId, e.getClass().getSimpleName());
                return message(e);
            }
        }
    }
    private record RuntimeModelSelection(ChatModel chatModel, boolean customModelUsed) {
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}







