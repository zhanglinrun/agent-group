package com.linrun.trigger.http;

import cn.hollis.llm.mentor.agent.agent.BaseAgent;
import cn.hollis.llm.mentor.agent.agent.deepresearch.PlanExecuteAgent;
import cn.hollis.llm.mentor.agent.agent.file.FileReactAgent;
import cn.hollis.llm.mentor.agent.agent.pptx.PPTBuilderAgent;
import cn.hollis.llm.mentor.agent.agent.skills.SkillsReactAgent;
import cn.hollis.llm.mentor.agent.agent.websearch.WebSearchReactAgent;
import cn.hollis.llm.mentor.agent.context.ContextPolicy;
import cn.hollis.llm.mentor.agent.entity.AiSession;
import cn.hollis.llm.mentor.agent.entity.record.FileInfo;
import cn.hollis.llm.mentor.agent.mapper.AiSessionMapper;
import cn.hollis.llm.mentor.agent.service.AgentTaskManager;
import cn.hollis.llm.mentor.agent.service.AiSessionService;
import cn.hollis.llm.mentor.agent.service.FileInfoService;
import cn.hollis.llm.mentor.agent.service.FileManageService;
import cn.hollis.llm.mentor.agent.tool.BashTool;
import cn.hollis.llm.mentor.agent.tool.FileContentService;
import cn.hollis.llm.mentor.agent.tool.FileSystemTools;
import cn.hollis.llm.mentor.agent.tool.GrepTool;
import cn.hollis.llm.mentor.agent.tool.SkillsTool;
import cn.hollis.llm.mentor.agent.tool.ToolMergeUtils;
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
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DodoNativeAgentService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DodoNativeAgentService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiSessionService sessionService;
    private final AgentTaskManager taskManager;
    private final FileContentService fileContentService;
    private final FileManageService fileManageService;
    private final FileInfoService fileInfoService;
    private final AiSessionMapper aiSessionMapper;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${tavily.mcp-url:https://mcp.tavily.com/mcp/}")
    private String tavilyMcpUrl;

    @Value("${skills.directory:}")
    private String skillsDirectory;

    private ToolCallback[] webSearchToolCallbacks = new ToolCallback[0];

    public DodoNativeAgentService(ObjectProvider<ChatModel> chatModelProvider,
                                  AiSessionService sessionService,
                                  AgentTaskManager taskManager,
                                  FileContentService fileContentService,
                                  FileManageService fileManageService,
                                  FileInfoService fileInfoService,
                                  AiSessionMapper aiSessionMapper,
                                  UserAccountService userAccountService,
                                  UserQuotaService userQuotaService) {
        this.chatModelProvider = chatModelProvider;
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.fileContentService = fileContentService;
        this.fileManageService = fileManageService;
        this.fileInfoService = fileInfoService;
        this.aiSessionMapper = aiSessionMapper;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
    }

    @Override
    public void afterPropertiesSet() {
        initWebSearchToolCallbacks();
    }

    public Flux<String> stream(String token, String agentType, String query, String conversationId, String fileId) {
        UserAccount user = user(token);
        String safeAgentType = normalizeAgentType(agentType);
        String safeConversationId = StringUtils.hasText(conversationId) ? conversationId.trim() : "S" + System.currentTimeMillis();
        String internalConversationId = internalConversationId(user.getUserId(), safeConversationId);
        BigDecimal quotaCost = userQuotaService.estimatePreCheckCost(safeAgentType);
        userQuotaService.assertEnoughQuota(user.getUserId(), quotaCost);
        validateFileAccess(user.getUserId(), internalConversationId, fileId);

        Flux<String> agentFlux = switch (safeAgentType) {
            case "file" -> withMemory(initFileReactAgent(user.getUserId()), internalConversationId).stream(internalConversationId, query, fileId);
            case "ppt" -> withMemory(initPPTBuilderAgent(), internalConversationId).execute(internalConversationId, query);
            case "deep" -> withMemory(initPlanExecuteAgent(), internalConversationId).stream(internalConversationId, query);
            case "skills" -> withMemory(initSkillsReactAgent(user.getUserId()), internalConversationId).stream(internalConversationId, query, fileId);
            default -> withMemory(initWebSearchAgent(), internalConversationId).stream(internalConversationId, query);
        };

        AtomicBoolean consumed = new AtomicBoolean(false);
        return agentFlux.doOnComplete(() -> {
            if (consumed.compareAndSet(false, true)) {
                consumeQuota(user.getUserId(), safeConversationId, safeAgentType);
                fillAgentType(internalConversationId, safeAgentType);
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

    public void deleteSession(String token, String conversationId) {
        UserAccount user = user(token);
        String internalConversationId = internalConversationId(user.getUserId(), conversationId);
        sessionService.remove(new LambdaQueryWrapper<AiSession>().eq(AiSession::getSessionId, internalConversationId));
    }

    public String externalConversationId(String userId, String sessionId) {
        String prefix = userId + ":";
        return sessionId != null && sessionId.startsWith(prefix) ? sessionId.substring(prefix.length()) : sessionId;
    }

    private void initWebSearchToolCallbacks() {
        if (!StringUtils.hasText(tavilyApiKey) || tavilyApiKey.contains("XXXXX") || !StringUtils.hasText(tavilyMcpUrl)) {
            webSearchToolCallbacks = new ToolCallback[0];
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
            webSearchToolCallbacks = provider.getToolCallbacks();
        } catch (Exception e) {
            LOGGER.warn("dodo tavily tool init failed, reason={}", e.getClass().getSimpleName());
            webSearchToolCallbacks = new ToolCallback[0];
        }
    }

    private WebSearchReactAgent initWebSearchAgent() {
        return WebSearchReactAgent.builder()
                .name("web react")
                .chatModel(chatModel())
                .tools(webSearchToolCallbacks)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(5)
                .build();
    }

    private FileReactAgent initFileReactAgent(String userId) {
        List<ToolCallback> tools = Arrays.asList(fileContentToolCallbacks(userId));
        return FileReactAgent.builder()
                .name("file react")
                .chatModel(chatModel())
                .tools(tools)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .build();
    }

    private PPTBuilderAgent initPPTBuilderAgent() {
        return new PPTBuilderAgent(chatModel(), Arrays.asList(webSearchToolCallbacks), sessionService, taskManager);
    }

    private PlanExecuteAgent initPlanExecuteAgent() {
        return PlanExecuteAgent.builder()
                .chatModel(chatModel())
                .tools(webSearchToolCallbacks)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .maxRounds(3)
                .build();
    }

    private SkillsReactAgent initSkillsReactAgent(String userId) {
        ToolCallback[] tools = ToolMergeUtils.mergeTools(
                webSearchToolCallbacks,
                fileContentToolCallbacks(userId),
                skillsToolCallbacks(),
                FileSystemTools.create(),
                GrepTool.create(),
                BashTool.create()
        );
        return SkillsReactAgent.builder()
                .name("skills")
                .chatModel(chatModel())
                .tools(tools)
                .sessionService(sessionService)
                .taskManager(taskManager)
                .contextPolicy(ContextPolicy.defaults())
                .build();
    }

    private ToolCallback[] fileContentToolCallbacks(String userId) {
        return ToolCallbacks.from(new OwnedFileContentTool(userId));
    }

    private ToolCallback[] skillsToolCallbacks() {
        if (!StringUtils.hasText(skillsDirectory)) {
            return new ToolCallback[0];
        }
        try {
            return new ToolCallback[]{SkillsTool.builder().addSkillsDirectory(skillsDirectory).build()};
        } catch (IllegalArgumentException e) {
            LOGGER.warn("dodo skills tool init skipped, reason={}", e.getMessage());
            return new ToolCallback[0];
        }
    }

    private <T extends BaseAgent> T withMemory(T agent, String conversationId) {
        ChatMemory persistentMemory = agent.createPersistentChatMemory(conversationId, 30);
        agent.setChatMemory(persistentMemory);
        return agent;
    }

    private ChatModel chatModel() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AppException("AGENT_0007", "模型客户端不可用，请检查大模型配置");
        }
        return chatModel;
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

    private void consumeQuota(String userId, String conversationId, String agentType) {
        userQuotaService.consumeForAcademicTask(userId, conversationId, agentType,
                GuideTokenUsage.empty(), "dodo-agent", 0L);
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
            default -> "chat";
        };
    }

    private String message(Throwable error) {
        return error == null || !StringUtils.hasText(error.getMessage()) ? "处理失败" : error.getMessage();
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
                LOGGER.warn("dodo file tool access denied or failed, fileId={}, reason={}", fileId, e.getClass().getSimpleName());
                return message(e);
            }
        }
    }
}
