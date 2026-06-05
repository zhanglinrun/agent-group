package com.linrun.trigger.http;

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
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${tavily.mcp-url:}")
    private String tavilyMcpUrl;

    @Value("${skills.directory:}")
    private String skillsDirectory;

    @Value("${skills.output-directory:outputs}")
    private String skillsOutputDirectory;

    @Value("${spring.ai.openai.chat.options.model:qwen3.6-plus}")
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
                                  AgentAdminConfigHandler agentAdminConfigHandler) {
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
        ChatModel runtimeChatModel = chatModel(internalConversationId, llmBaseUrl, llmApiKey, llmModel);
        BigDecimal quotaCost = userQuotaService.estimatePreCheckCost(safeAgentType);
        userQuotaService.assertEnoughQuota(user.getUserId(), quotaCost);
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
            case "trade-audit" -> withMemory(initWorkspaceReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, "trade", memoryPrompt), internalConversationId)
                    .stream(internalConversationId, workspaceQuery("trade", query), fileId);
            case "skills" -> withMemory(initSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "manual-skills" -> withMemory(initManualSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            default -> withMemory(initWebSearchAgent(internalConversationId, runtimeChatModel,
                            searchTools, webSearchEnabled, memoryPrompt), internalConversationId)
                    .stream(internalConversationId, query);
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
                                    observedContent.toString(), latencyMillis, tokenUsage);
                            fillAgentType(internalConversationId, safeAgentType);
                        }
                    } finally {
                        BearDoctorTokenUsageRecorder.clear(internalConversationId);
                    }
                });
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
        result.put("manualSkillsEndpoint", "/agent/skills/manual/stream");
        result.put("agentAdmin", agentAdminStatistics);
        result.put("agentAdminConfigCount", agentAdminStatistics.getOrDefault("configCount", 0));
        result.put("agentAdminEnabledCount", agentAdminStatistics.getOrDefault("enabledCount", 0));
        result.put("quotaMode", "spring-ai-usage-with-estimated-fallback");
        result.put("apiDocs", "/swagger-ui/index.html");
        result.put("agentExecutionModes", agentExecutionModes());
        List<Map<String, Object>> workspaceProfiles = workspaceProfiles(academicTools);
        result.put("workspaceProfiles", workspaceProfiles);
        result.put("toolRuntimeReadiness", toolRuntimeReadiness(academicTools, workspaceProfiles));
        result.put("capabilityMatrix", capabilityMatrix(academicTools, manualSkillCount, resolvedSkillsDirectory, agentAdminStatistics));
        result.put("toolCatalog", toolCatalog(academicTools, workspaceProfiles));
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

    private List<Map<String, Object>> agentExecutionModes() {
        return List.of(
                agentExecutionMode("chat", "对话助手", "react", "ReAct", "通用问答、交易解释和轻量工具调用"),
                agentExecutionMode("file", "文件问答", "react", "ReAct", "文件理解、引用回答和上下文追问"),
                agentExecutionMode("ppt", "PPT生成", "flow", "Flow", "需求澄清、大纲、搜索、模板和渲染状态流转"),
                agentExecutionMode("deep", "深度研究", "plan-execute", "Plan Execute", "计划拆解、分步执行、反思和动态重规划",
                        true,
                        List.of("plan_update/replan stream event",
                                "AcademicAgentFlowProgress.STATUS_REPLANNED",
                                "planner history versions")),
                agentExecutionMode("image", "图像生成", "react", "ReAct", "图像生成、图生图和多模态参考图处理"),
                agentExecutionMode("data", "数据问答", "react", "ReAct", "数据分析、表格检索和自然语言转 SQL"),
                agentExecutionMode("mrag", "MRAG 知识问答", "react", "ReAct", "多模态检索、知识库证据和资料交叉验证"),
                agentExecutionMode("trade-audit", "交易审计", "flow", "Trade Flow", "按支付、成团、额度到账和退款回滚流程核查交易闭环"),
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
        summary.put("source", skill.source() == null ? "" : skill.source().name());
        summary.put("allowedTools", skill.allowedTools() == null ? List.of() : skill.allowedTools());
        summary.put("scriptCount", skill.scripts() == null ? 0 : skill.scripts().size());
        summary.put("scripts", skill.buildScriptSummaries());
        return summary;
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
                workspaceProfile("trade", "/workspace/trade", "trade-audit", "none",
                        List.of("trade_audit", "planning", "data_analysis", "table_rag", "nl2sql", "report_tool"),
                        List.of("order", "quota", "status", "audit-report"),
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
                                                       int manualSkillCount,
                                                       String resolvedSkillsDirectory,
                                                       Map<String, Object> agentAdminStatistics) {
        List<String> toolNames = toolNames(academicTools);
        List<String> implementedTools = AcademicToolOutputNames.orderedRichToolNames();
        List<String> missingRuntimeTools = implementedTools.stream()
                .filter(toolName -> !toolNames.contains(toolName))
                .toList();
        long mcpToolCount = academicTools.stream()
                .filter(tool -> "mcp".equalsIgnoreCase(String.valueOf(tool.getOrDefault("category", ""))))
                .count();

        return List.of(
                capabilityItem(
                        "multi-agent",
                        "多智能体协同",
                        "ready",
                        "ReAct、Plan Execute、Flow 阶段推进、动态重规划和会话执行记忆已接入主链路。",
                        List.of("chat/file/skills 使用 ReAct 链路", "deep 使用 Plan Execute", "实时流和回放输出 plan_delta/flow_delta",
                                "flow_delta 支持 REPLANNED 状态", "plan_delta 支持 replan 计划版本", "同会话历史执行记忆会注入下一轮上下文"),
                        List.of()
                ),
                capabilityItem(
                        "tool-runtime",
                        "工具运行时",
                        "ready",
                        "统一工具注册、结构化输出、产物登记和运行账本已接入，外部端口未配置时按可用工具降级。",
                        implementedTools,
                        missingRuntimeTools
                ),
                capabilityItem(
                        "mcp",
                        "MCP 管理",
                        "ready",
                        "支持服务注册、启停、工具发现、缓存、后台配置导入和主 Agent 工具桥接。",
                        List.of("管理接口: /agent/mcp/admin/**",
                                "后台配置: agent.group.mcp.servers",
                                "状态文件: agent.group.mcp.admin-state-file",
                                "已缓存 MCP 工具数: " + mcpToolCount),
                        mcpToolCount > 0 ? List.of() : List.of("当前未发现或未缓存外部 MCP 工具")
                ),
                capabilityItem(
                        "agent-admin",
                        "Agent Admin Config",
                        "ready",
                        "Central admin surface for agent client, model, API, system prompt, advisor, RAG order and draw config.",
                        List.of("/api/v1/agent/admin/configs",
                                "/api/v1/agent/admin/statistics",
                                "configCount=" + agentAdminStatistics.getOrDefault("configCount", 0),
                                "enabledCount=" + agentAdminStatistics.getOrDefault("enabledCount", 0),
                                "categories=agent_client,model,api,system_prompt,advisor,rag_order,draw_config",
                                "state-file=agent.group.agent-admin.state-file"),
                        List.of()
                ),
                capabilityItem(
                        "workspace",
                        "前端工作区",
                        "ready",
                        "前端已提供 Agent、图像生成、数据问答、多模态知识问答和拼团交易工作区。",
                        List.of("/", "/workspace/image", "/workspace/data", "/workspace/mrag", "/workspace/trade"),
                        List.of()
                ),
                capabilityItem(
                        "skill-runtime",
                        "Skill 与脚本",
                        StringUtils.hasText(resolvedSkillsDirectory) ? "ready" : "degraded",
                        "支持手动技能读取、技能目录文件检索、脚本定义和会话产物目录。",
                        List.of("manualSkillCount=" + manualSkillCount,
                                "tools=read_skill/read_skill_file/grep_skill_files/glob_skill_files/list_skill_directory",
                                "skillsDirectory=" + (resolvedSkillsDirectory == null ? "" : resolvedSkillsDirectory)),
                        StringUtils.hasText(resolvedSkillsDirectory) ? List.of() : List.of("未配置 skills.directory")
                ),
                capabilityItem(
                        "trade-quota",
                        "交易与额度闭环",
                        "ready",
                        "直接购买、拼团成团、支付状态、额度发放、任务扣减和退款回滚通过后端交易系统控制。",
                        List.of("额度扣减模式: spring-ai-usage-with-estimated-fallback", "前端交易工作区: /workspace/trade"),
                        List.of()
                )
        );
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

    private List<String> fallbackInputFields(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> List.of("url", "query");
            case AcademicToolOutputNames.DATA_ANALYSIS -> List.of("task", "rows", "columns");
            case AcademicToolOutputNames.REPORT_TOOL -> List.of("title", "content", "format");
            case AcademicToolOutputNames.PLANNING -> List.of("goal", "context");
            case AcademicToolOutputNames.CODE_INTERPRETER -> List.of("task", "language", "code");
            case AcademicToolOutputNames.IMAGE_GENERATION -> List.of("prompt", "size", "mode");
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> List.of("question", "imageUrls", "fileIds");
            case AcademicToolOutputNames.DEEP_SEARCH -> List.of("query", "scope");
            case AcademicToolOutputNames.FILE_TOOL -> List.of("action", "fileId", "fileName");
            case AcademicToolOutputNames.SCRIPT_RUNNER -> List.of("scriptName", "runtime", "arguments");
            case AcademicToolOutputNames.TABLE_RAG -> List.of("query", "modelCodeList");
            case AcademicToolOutputNames.NL2SQL -> List.of("query", "schemaInfo");
            case AcademicToolOutputNames.TRADE_AUDIT -> List.of("question", "orderId", "userId");
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
            case AcademicToolOutputNames.TRADE_AUDIT -> List.of("order", "quota", "audit");
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
        String base = "启动 tools/reactor-tool/start.ps1，并设置 AGENT_GROUP_REACTOR_TOOL_ENABLED=true、AGENT_GROUP_REACTOR_TOOL_BASE_URL=http://127.0.0.1:1601。";
        return switch (toolName) {
            case AcademicToolOutputNames.TRADE_AUDIT -> "确认后端交易、拼团、额度仓储 Bean 已加载，trade_audit 会读取真实订单、支付、成团、退款和额度流水。";
            case AcademicToolOutputNames.IMAGE_GENERATION -> base + " 图像生成还需要在 reactor-tool 的 .env 中配置图像模型。";
            case AcademicToolOutputNames.DATA_ANALYSIS,
                 AcademicToolOutputNames.TABLE_RAG,
                 AcademicToolOutputNames.NL2SQL -> base + " 数据问答还需要配置表结构、数据库或向量检索相关环境变量。";
            case AcademicToolOutputNames.DEEP_SEARCH,
                 AcademicToolOutputNames.WEB_FETCH -> base + " 深度搜索和网页抓取还需要配置搜索或联网能力。";
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> base + " 多模态问答还需要配置视觉模型和文档解析依赖。";
            case AcademicToolOutputNames.SCRIPT_RUNNER,
                 AcademicToolOutputNames.CODE_INTERPRETER -> base + " 脚本和代码执行还需要确认 Python、Node 或系统命令可用。";
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
            case AcademicToolOutputNames.TRADE_AUDIT -> "trade";
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
            dynamicReplan.put("historyEvidence", List.of("AcademicReplayProjector", "planner history versions"));
            item.put("dynamicReplan", dynamicReplan);
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

    public String externalConversationId(String userId, String sessionId) {
        String prefix = userId + ":";
        return sessionId != null && sessionId.startsWith(prefix) ? sessionId.substring(prefix.length()) : sessionId;
    }

    private void initWebSearchToolCallbacks() {
        ToolCallback[] fallbackSearchTools = SearchTool.create(externalSearchService);
        if (!StringUtils.hasText(tavilyApiKey) || tavilyApiKey.contains("XXXXX") || !StringUtils.hasText(tavilyMcpUrl)) {
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
                academicToolCallbacks("ppt", userId, conversationId, webSearchEnabled)
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
        if (!StringUtils.hasText(skillsDirectory)) {
            return "";
        }
        String configured = skillsDirectory.trim();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(configured));
        candidates.add(cwd.resolve(configured).normalize());
        candidates.add(cwd.resolve("skills").normalize());
        candidates.add(cwd.resolve("..").resolve("skills").normalize());
        candidates.add(cwd.resolve("..").resolve("..").resolve("skills").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return configured;
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
                ? "- 本轮已开启联网搜索；需要最新公开信息时可以调用搜索工具、网页抓取或深度搜索工具。"
                : "- 本轮未开启联网搜索；不要调用搜索工具，也不要输出 ToolCall/search 文本。需要实时信息时请提示用户开启联网搜索后重试。";
        return """
                ## 技能产物输出规则
                - 所有生成文件必须写入当前会话输出目录或它的子目录。当前会话输出目录是：%s
                - 文件工具只允许访问当前会话输出目录；读取和写入时优先使用相对路径。
                %s
                - 普通用户环境没有 bash 和 grep 工具；需要抓取 Bilibili、抽帧或编译 LaTeX 时，使用 bilibili_fetch、extract_video_frames、compile_latex 这些专用工具。
                - 不要把文件生成到项目根目录、后端 app 目录、用户目录或系统临时目录。
                - 最终回答中不要暴露服务器本地绝对路径，例如 Windows 盘符路径或 Linux 绝对路径。
                - 只需要说明文件已经生成，PDF、LaTeX、字幕等文件会由前端下载按钮提供给用户。
                """.formatted(outputDirectory, webSearchRule);
    }

    private String workspaceRuntimePrompt(String outputDirectory, boolean webSearchEnabled, String workspace) {
        String base = skillRuntimePrompt(outputDirectory, webSearchEnabled);
        if ("image".equals(workspace)) {
            return ("""
                    ## 图像生成工作区
                    - 优先使用 image_generation 工具生成图片，必要时可先用 planning 拆解画面。
                    - 输出中要说明图像用途、画面要素、风格和可下载产物；不要只返回纯文本创意。
                    - 如果外部图像工具未配置，明确说明当前缺少图像生成端口，并给出可直接复用的生成提示词。

                    """ + base).trim();
        }
        if ("data".equals(workspace)) {
            return ("""
                    ## 数据问答工作区
                    - 优先使用 data_analysis、table_rag 或 nl2sql 工具处理表格、指标和查询问题。
                    - 涉及额度余额、订单状态、支付状态、拼团是否成团时，只能依据后端交易系统返回的数据，不要凭模型猜测。
                    - 如果没有接入真实数据源，要说明缺少数据端口，并输出可执行的分析口径、字段需求和校验步骤。

                    """ + base).trim();
        }
        if ("mrag".equals(workspace)) {
            return ("""
                    ## MRAG 多模态知识问答工作区
                    - 优先结合 file_tool、multimodal_agent、table_rag、deep_search 和 web_fetch 处理文档、图片、表格与外部资料。
                    - 先说明使用了哪些资料来源，再给结论、证据和不确定点；不要把模型猜测写成系统事实。
                    - 涉及额度、订单、支付、拼团状态时，仍以交易系统数据为准，不能由 Agent 自行判断到账或退款。
                    """ + base).trim();
        }
        if ("trade".equals(workspace)) {
            return ("""
                    ## 拼团交易审计工作区
                    - 用户提供订单号、队伍号、支付、退款、额度或成团问题时，优先调用 trade_audit 读取后端交易事实，再生成审计结论。
                    - 按 Flow 流程处理：识别交易对象 -> 核对订单状态 -> 核对支付状态 -> 核对拼团成团状态 -> 核对额度流水 -> 核对退款/回滚 -> 输出审计结论。
                    - 可以结合 planning、data_analysis、table_rag、nl2sql 和 report_tool；涉及实时订单、额度余额、支付结果时，以后端交易系统数据为准。
                    - 拼团支付成功不等于额度到账；只有成团结算或交易完成后才能发放额度。发现状态不一致时，要说明缺失证据和排查顺序。
                    """ + base).trim();
        }
        return base;
    }

    private String workspaceQuery(String workspace, String query) {
        String safeQuery = StringUtils.hasText(query) ? query.trim() : "";
        if ("image".equals(workspace)) {
            return """
                    请按图像生成工作区处理下面需求。优先调用 image_generation 工具，生成可复用图像产物。

                    需求：
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("data".equals(workspace)) {
            return """
                    请按数据问答工作区处理下面需求。优先调用 data_analysis、table_rag 或 nl2sql 工具，并把查询口径、结果和校验点说明清楚。

                    需求：
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("mrag".equals(workspace)) {
            return """
                    请按 MRAG 多模态知识问答工作区处理下面需求。优先结合文件、图片、表格、知识检索和网页资料，输出结论、证据来源和不确定点。

                    需求：
                    %s
                    """.formatted(safeQuery).trim();
        }
        if ("trade".equals(workspace)) {
            return """
                    请按拼团交易审计 Flow 处理下面需求。优先调用 trade_audit 核对后端交易事实；必须区分订单状态、支付状态、拼团成团状态、额度流水、退款回滚和 Agent 消耗流水；不要把支付成功直接判断为额度已到账。

                    需求：
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
                    agentAdminCategoryPrompt("system_prompt", "系统提示词", safeAgentType),
                    agentAdminCategoryPrompt("advisor", "Advisor 配置", safeAgentType),
                    agentAdminCategoryPrompt("rag_order", "RAG 顺序", safeAgentType),
                    "image".equals(safeAgentType)
                            ? agentAdminCategoryPrompt("draw_config", "图像生成配置", safeAgentType)
                            : "");
            if (!StringUtils.hasText(prompt)) {
                return "";
            }
            return "## Agent 后台启用配置\n" + prompt;
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
        return switch (normalizeAgentType(agentType)) {
            case "trade-audit" -> "trade";
            default -> normalizeAgentType(agentType);
        };
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
                ## 联网搜索状态
                本轮未开启联网搜索。不要调用搜索工具，也不要输出 ToolCall/search 文本。
                普通知识和上下文足够时直接回答；确实需要实时信息时，请提示用户开启联网搜索后重试。
                """;
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

    private ChatModel chatModel(String conversationId,
                                String llmBaseUrl,
                                String llmApiKey,
                                String llmModel) {
        if (hasCustomModelConfig(llmBaseUrl, llmApiKey, llmModel)) {
            ChatModel chatModel = customChatModel(llmBaseUrl, llmApiKey, llmModel);
            return new UsageRecordingChatModel(chatModel, conversationId);
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AppException("AGENT_0007", "模型客户端不可用，请检查大模型配置");
        }
        return new UsageRecordingChatModel(chatModel, conversationId);
    }

    private ChatModel customChatModel(String llmBaseUrl, String llmApiKey, String llmModel) {
        if (!StringUtils.hasText(llmBaseUrl) || !StringUtils.hasText(llmApiKey)) {
            throw new AppException("AGENT_0010", "自定义模型配置不完整，请填写 API 地址和密钥");
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
            throw new AppException("AGENT_0011", "自定义 API 地址格式不正确");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || !StringUtils.hasText(host)) {
            throw new AppException("AGENT_0011", "自定义 API 地址仅支持 HTTPS");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            throw new AppException("AGENT_0011", "自定义 API 地址不能指向本地或内网地址");
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
        FileInfo fileInfo = fileManageService.getFileInfo(fileId);
        assertOwnedFile(userId, fileInfo);
    }

    private void assertOwnedFile(String userId, FileInfo fileInfo) {
        String prefix = userId + ":";
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getConversationId())
                || !fileInfo.getConversationId().startsWith(prefix)) {
            throw new AppException("FILE_0001", "文件不存在或无权访问");
        }
    }

    private void consumeQuota(String userId,
                              String conversationId,
                              String agentType,
                              String query,
                              String observedContent,
                              long latencyMillis,
                              BearDoctorTokenUsageRecorder.Snapshot tokenUsage) {
        GuideTokenUsage usage = hasRealUsage(tokenUsage)
                ? new GuideTokenUsage(tokenUsage.promptTokens(), tokenUsage.completionTokens(),
                tokenUsage.totalTokens(), BigDecimal.ZERO)
                : estimateTokenUsage(query, observedContent);
        String model = hasRealUsage(tokenUsage) && StringUtils.hasText(tokenUsage.model())
                ? tokenUsage.model()
                : "bear-doctor-agent-estimated";
        userQuotaService.consumeForAcademicTask(userId, conversationId, agentType,
                usage, model, latencyMillis);
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

    private GuideTokenUsage estimateTokenUsage(String query, String observedContent) {
        long promptTokens = estimateTokens(query);
        long completionTokens = estimateTokens(observedContent);
        return new GuideTokenUsage(promptTokens, completionTokens, promptTokens + completionTokens, BigDecimal.ZERO);
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
            throw new AppException("0001", "会话编号不能为空");
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
            case "data", "data-qa", "workspace-data", "nl2sql", "table-rag" -> "data";
            case "mrag", "multi-modal-rag", "multimodal-rag", "workspace-mrag" -> "mrag";
            case "trade", "trade-audit", "trade-flow", "group-trade", "workspace-trade" -> "trade-audit";
            case "skills" -> "skills";
            case "manual", "manual-skills", "skills-manual" -> "manual-skills";
            default -> "chat";
        };
    }

    private boolean isTavilyConfigured() {
        return StringUtils.hasText(tavilyApiKey)
                && !tavilyApiKey.contains("XXXXX");
    }

    private String webSearchStatus() {
        return webSearchStatus;
    }

    private String message(Throwable error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return "处理失败";
        }
        String message = error.getMessage();
        String lower = message.toLowerCase();
        if ((lower.contains("duplicate entry") || lower.contains("sqlintegrityconstraintviolationexception"))
                && (lower.contains("uk_user_biz_flow") || lower.contains("user_quota_flow"))) {
            return "本次请求已处理，请勿重复提交或刷新后重试";
        }
        return message;
    }

    private class OwnedFileContentTool {
        private final String userId;

        private OwnedFileContentTool(String userId) {
            this.userId = userId;
        }

        @Tool(description = "根据文件ID加载文件内容或进行RAG语义检索，仅允许访问当前登录用户自己的文件。")
        public String loadContent(
                @ToolParam(description = "文件ID") String fileId,
                @ToolParam(description = "用户的问题，用于语义检索（可选）") String question) {
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
}
