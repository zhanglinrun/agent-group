package com.linrun.trigger.http;

import com.linrun.trigger.agent.agent.BaseAgent;
import com.linrun.trigger.agent.agent.deepresearch.PlanExecuteAgent;
import com.linrun.trigger.agent.agent.file.FileReactAgent;
import com.linrun.trigger.agent.agent.pptx.PPTBuilderAgent;
import com.linrun.trigger.agent.agent.skills.SkillsReactAgent;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeTools;
import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.config.SkillConfig;
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
import com.linrun.trigger.agent.tool.SearchTool;
import com.linrun.trigger.agent.tool.SkillsTool;
import com.linrun.trigger.agent.tool.ToolMergeUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
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
                                  AcademicExternalSearchService externalSearchService) {
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

        Flux<String> agentFlux = switch (safeAgentType) {
            case "file" -> withMemory(initFileReactAgent(user.getUserId(), internalConversationId, runtimeChatModel), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "ppt" -> withMemory(initPPTBuilderAgent(internalConversationId, runtimeChatModel, searchTools), internalConversationId)
                    .execute(internalConversationId, query);
            case "deep" -> withMemory(initPlanExecuteAgent(internalConversationId, runtimeChatModel, searchTools), internalConversationId)
                    .stream(internalConversationId, query);
            case "skills" -> withMemory(initSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel, searchTools, webSearchEnabled), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            case "manual-skills" -> withMemory(initManualSkillsReactAgent(user.getUserId(), internalConversationId, runtimeChatModel, searchTools, webSearchEnabled), internalConversationId)
                    .stream(internalConversationId, query, fileId);
            default -> withMemory(initWebSearchAgent(internalConversationId, runtimeChatModel, searchTools, webSearchEnabled), internalConversationId)
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
        result.put("chatModelAvailable", chatModelProvider.getIfAvailable() != null);
        result.put("tavilyConfigured", isTavilyConfigured());
        result.put("webSearchAvailable", webSearchToolCallbacks.length > 0);
        result.put("webSearchToolCount", webSearchToolCallbacks.length);
        result.put("webSearchStatus", webSearchStatus());
        result.put("skillsDirectoryConfigured", StringUtils.hasText(resolvedSkillsDirectory));
        result.put("skillsDirectory", resolvedSkillsDirectory);
        result.put("skillsOutputDirectory", resolvedSkillsOutputDirectory());
        result.put("skillsToolAvailable", skillsToolCallbacks().length > 0);
        result.put("manualSkillsAvailable", manualSkillCount > 0);
        result.put("manualSkillCount", manualSkillCount);
        result.put("manualSkillsEndpoint", "/agent/skills/manual/stream");
        result.put("quotaMode", "spring-ai-usage-with-estimated-fallback");
        result.put("apiDocs", "/swagger-ui/index.html");
        return result;
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
                                                   boolean webSearchEnabled) {
        return WebSearchReactAgent.builder()
                .name("web react")
                .chatModel(chatModel)
                .tools(searchTools)
                .systemPrompt(webSearchEnabled ? "" : webSearchDisabledPrompt())
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(5)
                .build();
    }

    private FileReactAgent initFileReactAgent(String userId, String conversationId, ChatModel chatModel) {
        List<ToolCallback> tools = Arrays.asList(fileContentToolCallbacks(userId));
        return FileReactAgent.builder()
                .name("file react")
                .chatModel(chatModel)
                .tools(tools)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .build();
    }

    private PPTBuilderAgent initPPTBuilderAgent(String conversationId, ChatModel chatModel, ToolCallback[] searchTools) {
        return new PPTBuilderAgent(chatModel, Arrays.asList(searchTools), sessionService, taskManager);
    }

    private PlanExecuteAgent initPlanExecuteAgent(String conversationId, ChatModel chatModel, ToolCallback[] searchTools) {
        return PlanExecuteAgent.builder()
                .chatModel(chatModel)
                .tools(searchTools)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(3)
                .build();
    }

    private SkillsReactAgent initSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                  ToolCallback[] searchTools,
                                                  boolean webSearchEnabled) {
        String outputDirectory = sessionSkillsOutputDirectory(conversationId);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                skillsToolCallbacks(),
                SkillRuntimeTools.create(resolvedSkillsDirectory(), projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name("skills")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt(skillRuntimePrompt(outputDirectory, webSearchEnabled))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .contextPolicy(ContextPolicy.defaults())
                .build();
    }

    private SkillsReactAgent initManualSkillsReactAgent(String userId, String conversationId, ChatModel chatModel,
                                                        ToolCallback[] searchTools,
                                                        boolean webSearchEnabled) {
        String outputDirectory = sessionSkillsOutputDirectory(conversationId);
        SkillManager skillManager = manualSkillManager();
        ToolCallback[] readSkillTools = skillManager == null
                ? new ToolCallback[0]
                : new ToolCallback[]{ReadSkillTool.create(skillManager.getRegistry())};
        String skillsPrompt = skillManager == null ? "" : skillManager.formatPrompt();
        String runtimePrompt = skillRuntimePrompt(outputDirectory, webSearchEnabled);
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                searchTools,
                fileContentToolCallbacks(userId),
                readSkillTools,
                SkillRuntimeTools.create(resolvedSkillsDirectory(), projectRoot().toString(), outputDirectory)
        );
        return SkillsReactAgent.builder()
                .name("manual-skills")
                .chatModel(chatModel)
                .tools(tools)
                .systemPrompt((skillsPrompt + "\n" + runtimePrompt).trim())
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(10)
                .contextPolicy(ContextPolicy.defaults())
                .build();
    }

    private ToolCallback[] fileContentToolCallbacks(String userId) {
        return ToolCallbacks.from(new OwnedFileContentTool(userId));
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
                ? "- 本轮已开启联网搜索；需要最新公开信息时可以调用搜索工具。"
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

    private String webSearchDisabledPrompt() {
        return """
                ## 联网搜索状态
                本轮未开启联网搜索。不要调用搜索工具，也不要输出 ToolCall/search 文本。
                普通知识和上下文足够时直接回答；确实需要实时信息时，请提示用户开启联网搜索后重试。
                """;
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
