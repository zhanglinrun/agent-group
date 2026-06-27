package com.linrun.trigger.http.agent.support;

import com.linrun.trigger.http.agent.AcademicExternalSearchService;
import com.linrun.trigger.http.agent.ApiKeyValidator;
import com.linrun.trigger.agent.tool.SearchTool;
import com.linrun.trigger.agent.tool.ToolMergeUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/**
 * Agent 的联网搜索 MCP 客户端，从 AcademicAgentNativeService 抽出。
 * 把原先散在 Service 里的手写 MCP 初始化（Tavily Streamable HTTP transport + 直连 API 回退）集中到一处，
 * 既给 stream 编排提供搜索工具回调，又给能力展示提供搜索状态。
 */
@Component
public class AcademicWebSearchMcpClient implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcademicWebSearchMcpClient.class);

    private final AcademicExternalSearchService externalSearchService;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${tavily.mcp-url:}")
    private String tavilyMcpUrl;

    private ToolCallback[] webSearchToolCallbacks = new ToolCallback[0];
    private String webSearchStatus = "missing-config";

    public AcademicWebSearchMcpClient(AcademicExternalSearchService externalSearchService) {
        this.externalSearchService = externalSearchService;
    }

    @Override
    public void afterPropertiesSet() {
        init();
    }

    public void init() {
        ToolCallback[] fallbackSearchTools = SearchTool.create(externalSearchService);
        if (!ApiKeyValidator.isValidApiKey(tavilyApiKey) || !StringUtils.hasText(tavilyMcpUrl)) {
            LOGGER.warn("academic-agent tavily tool init skipped, reason=missing_config");
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
            LOGGER.warn("academic-agent tavily tool init failed, reason={}", e.getClass().getSimpleName());
            webSearchToolCallbacks = fallbackSearchTools;
            webSearchStatus = fallbackSearchTools.length > 0 ? "direct-api-fallback" : "configured-but-no-tools";
        }
    }

    public ToolCallback[] getToolCallbacks() {
        return webSearchToolCallbacks;
    }

    public boolean isTavilyConfigured() {
        return ApiKeyValidator.isValidApiKey(tavilyApiKey);
    }

    public String getWebSearchStatus() {
        return webSearchStatus;
    }
}
