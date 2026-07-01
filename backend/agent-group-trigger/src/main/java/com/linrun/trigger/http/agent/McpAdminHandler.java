package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.runtime.tool.mcp.AgentMcpRegistry;
import com.linrun.domain.agent.runtime.tool.mcp.AgentMcpRegistrySummary;
import com.linrun.domain.agent.runtime.tool.mcp.AgentMcpServerDescriptor;
import com.linrun.domain.agent.runtime.tool.mcp.AgentMcpToolDescriptor;
import com.linrun.trigger.config.McpAdminProperties;
import com.linrun.types.exception.AppException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class McpAdminHandler {

    private static final Logger log = LoggerFactory.getLogger(McpAdminHandler.class);

    private final AgentMcpRegistry registry;
    private final McpToolDiscoverer toolDiscoverer;
    private final McpToolInvoker toolInvoker;
    private final ObjectMapper objectMapper;
    private final Path stateFile;

    public McpAdminHandler() {
        this(new AgentMcpRegistry(), new SdkMcpToolDiscoverer(), new SdkMcpToolInvoker(),
                new ObjectMapper().findAndRegisterModules(), "", false);
    }

    @Autowired
    public McpAdminHandler(ObjectMapper objectMapper, McpAdminProperties properties) {
        this(new AgentMcpRegistry(), new SdkMcpToolDiscoverer(), new SdkMcpToolInvoker(),
                objectMapper, properties == null ? "" : properties.getAdminStateFile(), true);
        importConfiguredState(properties);
    }

    McpAdminHandler(McpToolDiscoverer toolDiscoverer) {
        this(new AgentMcpRegistry(), toolDiscoverer, new SdkMcpToolInvoker(),
                new ObjectMapper().findAndRegisterModules(), "", false);
    }

    McpAdminHandler(McpToolDiscoverer toolDiscoverer, McpToolInvoker toolInvoker) {
        this(new AgentMcpRegistry(), toolDiscoverer, toolInvoker,
                new ObjectMapper().findAndRegisterModules(), "", false);
    }

    McpAdminHandler(McpToolDiscoverer toolDiscoverer, Path stateFile) {
        this(new AgentMcpRegistry(), toolDiscoverer, new SdkMcpToolInvoker(), new ObjectMapper().findAndRegisterModules(),
                stateFile == null ? "" : stateFile.toString(), false);
    }

    McpAdminHandler(McpToolDiscoverer toolDiscoverer,
                    McpToolInvoker toolInvoker,
                    Path stateFile,
                    McpAdminProperties properties) {
        this(new AgentMcpRegistry(), toolDiscoverer, toolInvoker, new ObjectMapper().findAndRegisterModules(),
                stateFile == null ? "" : stateFile.toString(), false);
        importConfiguredState(properties);
    }

    private McpAdminHandler(AgentMcpRegistry registry,
                            McpToolDiscoverer toolDiscoverer,
                            McpToolInvoker toolInvoker,
                            ObjectMapper objectMapper,
                            String stateFile,
                            boolean useDefaultStateFile) {
        this.registry = registry == null ? new AgentMcpRegistry() : registry;
        this.toolDiscoverer = toolDiscoverer == null ? new SdkMcpToolDiscoverer() : toolDiscoverer;
        this.toolInvoker = toolInvoker == null ? new SdkMcpToolInvoker() : toolInvoker;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.stateFile = resolveStateFile(stateFile, useDefaultStateFile);
        loadState();
    }

    public Map<String, Object> registerServer(Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String serverId = text(body.get("serverId"));
        String transport = defaultText(body.get("transport"), "streamable_http");
        String endpoint = text(body.get("endpoint"));
        if (!StringUtils.hasText(endpoint) && isStdioTransport(transport)) {
            endpoint = "stdio://" + serverId;
        }
        AgentMcpServerDescriptor server = AgentMcpServerDescriptor.builder(serverId)
                .name(text(body.get("name")))
                .endpoint(endpoint)
                .transport(transport)
                .enabled(bool(body.get("enabled"), true))
                .metadata(map(body.get("metadata")))
                .build();
        registry.registerServer(server);
        toolInvoker.invalidate(server.getServerId());
        persistState();
        return server(server);
    }

    public Map<String, Object> enableServer(String serverId, boolean enabled) {
        registry.enableServer(serverId, enabled);
        toolInvoker.invalidate(serverId);
        persistState();
        return registry.listServers().stream()
                .filter(server -> server.getServerId().equals(serverId))
                .findFirst()
                .map(this::server)
                .orElseThrow(() -> new AppException("MCP_0201", "mcp server not found after update: " + serverId));
    }

    public Map<String, Object> discoverTools(String serverId, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        AgentMcpServerDescriptor server = requireServer(serverId);
        List<AgentMcpToolDescriptor> tools = toolDiscoverer.discover(server, body);
        boolean cache = bool(body.get("cache"), true);
        if (cache) {
            registry.cacheDiscoveredTools(serverId, tools);
            persistState();
        }
        return Map.of(
                "serverId", serverId,
                "toolCount", tools.size(),
                "cached", cache,
                "tools", tools.stream().map(this::tool).toList(),
                "lastDiscoveredAt", registry.lastDiscoveredAt(serverId).map(LocalDateTime::toString).orElse(""),
                "cache", cacheState(server));
    }

    public Map<String, Object> cacheTools(String serverId, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        List<AgentMcpToolDescriptor> tools = list(body.get("tools")).stream()
                .filter(Map.class::isInstance)
                .map(item -> tool(serverId, map(item)))
                .toList();
        registry.cacheDiscoveredTools(serverId, tools);
        persistState();
        return Map.of(
                "serverId", serverId,
                "toolCount", tools.size(),
                "tools", registry.listTools(serverId).stream().map(this::tool).toList(),
                "lastDiscoveredAt", registry.lastDiscoveredAt(serverId).map(LocalDateTime::toString).orElse(""),
                "cache", cacheState(requireServer(serverId)));
    }

    public List<Map<String, Object>> listServers() {
        return registry.listServers().stream().map(this::server).toList();
    }

    public List<Map<String, Object>> listTools(String serverId, boolean enabledOnly) {
        List<AgentMcpToolDescriptor> tools = enabledOnly
                ? registry.listEnabledTools()
                : registry.listTools(serverId);
        return tools.stream().map(this::tool).toList();
    }

    public List<Map<String, Object>> listAgentToolDefinitions() {
        return listAgentReadyTools().stream()
                .map(this::agentTool)
                .toList();
    }

    public Map<String, Object> exportState() {
        AgentMcpRegistry.Snapshot snapshot = registry.snapshot();
        List<Map<String, Object>> servers = listServers();
        List<Map<String, Object>> tools = listTools("", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshot", snapshot);
        result.put("servers", servers);
        result.put("tools", tools);
        result.put("serverCount", servers.size());
        result.put("toolCount", tools.size());
        result.put("enabledServerCount", servers.stream().filter(server -> bool(server.get("enabled"), false)).count());
        result.put("enabledToolCount", tools.stream().filter(tool -> bool(tool.get("enabled"), false)).count());
        result.put("stateFile", stateFile == null ? "" : stateFile.toString());
        return result;
    }

    public Map<String, Object> importState(Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        boolean replace = bool(body.get("replace"), false);
        AgentMcpRegistry.Snapshot imported = snapshot(body);
        AgentMcpRegistry.Snapshot next = replace ? imported : mergeSnapshots(registry.snapshot(), imported);
        registry.restore(next);
        safeServers(next).forEach(server -> toolInvoker.invalidate(server.serverId()));
        persistState();
        return Map.of(
                "replace", replace,
                "serverCount", registry.listServers().size(),
                "toolCount", registry.listTools("").size(),
                "enabledToolCount", registry.listEnabledTools().size());
    }

    public Map<String, Object> health() {
        AgentMcpRegistrySummary summary = registry.summary();
        List<Map<String, Object>> serverChecks = registry.listServers().stream()
                .map(this::serverHealth)
                .toList();
        long enabledServerCount = serverChecks.stream()
                .filter(item -> bool(item.get("enabled"), false))
                .count();
        long readyServerCount = serverChecks.stream()
                .filter(item -> "ready".equals(item.get("status")))
                .count();
        long degradedServerCount = serverChecks.stream()
                .filter(item -> "degraded".equals(item.get("status")))
                .count();
        String overallStatus = serverChecks.isEmpty()
                ? "empty"
                : enabledServerCount == 0
                ? "disabled"
                : degradedServerCount == 0 && readyServerCount == enabledServerCount ? "ready" : "degraded";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallStatus", overallStatus);
        result.put("serverCount", serverChecks.size());
        result.put("enabledServerCount", enabledServerCount);
        result.put("readyServerCount", readyServerCount);
        result.put("degradedServerCount", degradedServerCount);
        result.put("toolCount", registry.listTools("").size());
        result.put("enabledToolCount", registry.listEnabledTools().size());
        result.put("registrySummary", registrySummary(summary));
        result.put("servers", serverChecks);
        return result;
    }

    public Map<String, Object> callAgentTool(String agentToolName, Map<String, Object> arguments) {
        AgentMcpToolDescriptor tool = resolveAgentTool(agentToolName);
        AgentMcpServerDescriptor server = requireServer(tool.getServerId());
        ensureToolCallable(server, tool);
        return toolInvoker.invoke(server, tool, arguments == null ? Map.of() : arguments);
    }

    public Map<String, Object> callRegisteredTool(String toolName, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        Map<String, Object> arguments = body.containsKey("arguments") ? map(body.get("arguments")) : body;
        String name = text(toolName);
        AgentMcpToolDescriptor tool = registry.findTool(name)
                .orElseGet(() -> resolveAgentTool(name));
        AgentMcpServerDescriptor server = requireServer(tool.getServerId());
        ensureToolCallable(server, tool);
        return toolInvoker.invoke(server, tool, arguments);
    }

    private Map<String, Object> registrySummary(AgentMcpRegistrySummary summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverCount", summary.serverCount());
        result.put("enabledServerCount", summary.enabledServerCount());
        result.put("disabledServerCount", summary.disabledServerCount());
        result.put("registeredToolCount", summary.registeredToolCount());
        result.put("enabledToolCount", summary.enabledToolCount());
        result.put("cachedServerCount", summary.cachedServerCount());
        result.put("emptyCacheServerCount", summary.emptyCacheServerCount());
        result.put("cachedServerIds", summary.cachedServerIds());
        result.put("serversWithoutCachedTools", summary.serversWithoutCachedTools());
        result.put("enabledServersWithoutCachedTools", summary.enabledServersWithoutCachedTools());
        result.put("transportCounts", summary.transportCounts());
        result.put("hasEnabledServerWithoutCache", summary.hasEnabledServerWithoutCache());
        result.put("hasEnabledTool", summary.hasEnabledTool());
        return result;
    }

    private Map<String, Object> serverHealth(AgentMcpServerDescriptor server) {
        List<AgentMcpToolDescriptor> tools = registry.listTools(server.getServerId());
        String lastDiscoveredAt = registry.lastDiscoveredAt(server.getServerId())
                .map(LocalDateTime::toString)
                .orElse("");
        String status;
        String message;
        if (!server.isEnabled()) {
            status = "disabled";
            message = "server disabled";
        } else if (tools.isEmpty()) {
            status = "degraded";
            message = "no cached tools, discover tools before agent use";
        } else if (cacheExpired(server)) {
            status = "degraded";
            message = "cached tools expired, rediscover tools before agent use";
        } else {
            status = "ready";
            message = "cached tools ready";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverId", server.getServerId());
        result.put("name", server.getName());
        result.put("transport", server.getTransport());
        result.put("endpoint", server.getEndpoint());
        result.put("enabled", server.isEnabled());
        result.put("status", status);
        result.put("message", message);
        result.put("toolCount", tools.size());
        result.put("enabledToolCount", tools.stream().filter(AgentMcpToolDescriptor::isEnabled).count());
        result.put("lastDiscoveredAt", lastDiscoveredAt);
        result.putAll(cacheState(server));
        return result;
    }

    private AgentMcpRegistry.Snapshot snapshot(Map<String, Object> body) {
        Object candidate = body.get("snapshot");
        if (candidate == null) {
            candidate = body;
        }
        try {
            return objectMapper.convertValue(candidate, AgentMcpRegistry.Snapshot.class);
        } catch (IllegalArgumentException e) {
            throw new AppException("MCP_0303", "mcp admin state import failed: " + e.getMessage(), e);
        }
    }

    private AgentMcpRegistry.Snapshot mergeSnapshots(AgentMcpRegistry.Snapshot current,
                                                        AgentMcpRegistry.Snapshot imported) {
        Map<String, AgentMcpRegistry.ServerState> servers = new LinkedHashMap<>();
        safeServers(current).forEach(server -> servers.put(server.serverId(), server));
        safeServers(imported).forEach(server -> {
            if (StringUtils.hasText(server.serverId())) {
                servers.put(server.serverId(), server);
            }
        });

        Map<String, List<AgentMcpRegistry.ToolState>> toolsByServer = new LinkedHashMap<>();
        mergeToolStates(toolsByServer, current);
        mergeToolStates(toolsByServer, imported);

        Map<String, String> discoveredAt = new LinkedHashMap<>();
        discoveredAt.putAll(safeDiscovered(current));
        discoveredAt.putAll(safeDiscovered(imported));
        return new AgentMcpRegistry.Snapshot(new ArrayList<>(servers.values()), toolsByServer, discoveredAt);
    }

    private void mergeToolStates(Map<String, List<AgentMcpRegistry.ToolState>> result,
                                 AgentMcpRegistry.Snapshot snapshot) {
        safeTools(snapshot).forEach((serverId, tools) -> {
            Map<String, AgentMcpRegistry.ToolState> byName = new LinkedHashMap<>();
            result.getOrDefault(serverId, List.of()).forEach(tool -> byName.put(tool.toolName(), tool));
            if (tools != null) {
                tools.stream()
                        .filter(tool -> tool != null && StringUtils.hasText(tool.toolName()))
                        .forEach(tool -> byName.put(tool.toolName(), tool));
            }
            result.put(serverId, new ArrayList<>(byName.values()));
        });
    }

    private List<AgentMcpRegistry.ServerState> safeServers(AgentMcpRegistry.Snapshot snapshot) {
        return snapshot == null || snapshot.servers() == null ? List.of() : snapshot.servers();
    }

    private Map<String, List<AgentMcpRegistry.ToolState>> safeTools(AgentMcpRegistry.Snapshot snapshot) {
        return snapshot == null || snapshot.toolsByServer() == null ? Map.of() : snapshot.toolsByServer();
    }

    private Map<String, String> safeDiscovered(AgentMcpRegistry.Snapshot snapshot) {
        return snapshot == null || snapshot.discoveredAtByServer() == null ? Map.of() : snapshot.discoveredAtByServer();
    }

    private void importConfiguredState(McpAdminProperties properties) {
        if (properties == null || properties.getServers().isEmpty()) {
            return;
        }
        boolean changed = false;
        for (McpAdminProperties.Server configuredServer : properties.getServers()) {
            if (configuredServer == null || !StringUtils.hasText(configuredServer.getServerId())) {
                continue;
            }
            try {
                AgentMcpServerDescriptor server = server(configuredServer);
                registry.registerServer(server);
                toolInvoker.invalidate(server.getServerId());
                changed = true;

                List<AgentMcpToolDescriptor> tools = configuredTools(server.getServerId(), configuredServer.getTools());
                if (!tools.isEmpty()) {
                    registry.cacheDiscoveredTools(server.getServerId(), tools);
                    changed = true;
                }

                if (configuredServer.isDiscoverOnStartup()) {
                    changed = discoverConfiguredTools(server, configuredServer) || changed;
                }
            } catch (RuntimeException e) {
                log.warn("MCP config import skipped: serverId={}, reason={}",
                        configuredServer.getServerId(), e.getMessage());
            }
        }
        if (changed && properties.isPersistImportedState()) {
            persistState();
        }
    }

    private boolean discoverConfiguredTools(AgentMcpServerDescriptor server,
                                            McpAdminProperties.Server configuredServer) {
        Map<String, Object> request = new LinkedHashMap<>(configuredServer.getDiscoveryRequest());
        request.putIfAbsent("cache", configuredServer.isCacheDiscoveredTools());
        try {
            List<AgentMcpToolDescriptor> tools = toolDiscoverer.discover(server, request);
            if (configuredServer.isCacheDiscoveredTools()) {
                registry.cacheDiscoveredTools(server.getServerId(), tools);
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("MCP startup discovery skipped: serverId={}, reason={}", server.getServerId(), e.getMessage());
        }
        return false;
    }

    private AgentMcpServerDescriptor server(McpAdminProperties.Server configuredServer) {
        String serverId = text(configuredServer.getServerId());
        String transport = defaultText(configuredServer.getTransport(), "streamable_http");
        String endpoint = text(configuredServer.getEndpoint());
        if (!StringUtils.hasText(endpoint) && isStdioTransport(transport)) {
            endpoint = "stdio://" + serverId;
        }
        return AgentMcpServerDescriptor.builder(serverId)
                .name(configuredServer.getName())
                .endpoint(endpoint)
                .transport(transport)
                .enabled(configuredServer.isEnabled())
                .metadata(configuredServer.getMetadata())
                .build();
    }

    private List<AgentMcpToolDescriptor> configuredTools(String serverId,
                                                            List<McpAdminProperties.Tool> configuredTools) {
        if (configuredTools == null || configuredTools.isEmpty()) {
            return List.of();
        }
        return configuredTools.stream()
                .filter(Objects::nonNull)
                .filter(tool -> StringUtils.hasText(defaultText(tool.getToolName(), tool.getName())))
                .map(tool -> AgentMcpToolDescriptor.builder(serverId, defaultText(tool.getToolName(), tool.getName()))
                        .description(tool.getDescription())
                        .inputSchema(tool.getInputSchema())
                        .enabled(tool.isEnabled())
                        .build())
                .toList();
    }

    private AgentMcpServerDescriptor requireServer(String serverId) {
        return registry.listServers().stream()
                .filter(server -> server.getServerId().equals(serverId))
                .findFirst()
                .orElseThrow(() -> new AppException("MCP_0102", "unknown mcp server: " + serverId));
    }

    private AgentMcpToolDescriptor resolveAgentTool(String agentToolName) {
        return registry.listEnabledTools().stream()
                .filter(tool -> agentToolName(tool).equals(agentToolName))
                .findFirst()
                .orElseThrow(() -> new AppException("MCP_0400", "unknown agent mcp tool: " + agentToolName));
    }

    private List<AgentMcpToolDescriptor> listAgentReadyTools() {
        return registry.listEnabledTools().stream()
                .filter(tool -> !cacheExpired(requireServer(tool.getServerId())))
                .toList();
    }

    private void ensureToolCallable(AgentMcpServerDescriptor server, AgentMcpToolDescriptor tool) {
        if (!server.isEnabled() || !tool.isEnabled()) {
            throw new AppException("MCP_0401", "mcp tool disabled: " + tool.qualifiedName());
        }
        if (cacheExpired(server)) {
            throw new AppException("MCP_0404", "mcp tool cache expired, rediscover tools before agent use: "
                    + tool.qualifiedName());
        }
    }

    private AgentMcpToolDescriptor tool(String serverId, Map<String, Object> body) {
        String toolName = defaultText(body.get("toolName"), text(body.get("name")));
        if (!StringUtils.hasText(toolName)) {
            throw new AppException("MCP_0202", "mcp tool name cannot be blank");
        }
        return AgentMcpToolDescriptor.builder(serverId, toolName)
                .description(text(body.get("description")))
                .inputSchema(map(body.get("inputSchema")))
                .enabled(bool(body.get("enabled"), true))
                .build();
    }

    private Map<String, Object> server(AgentMcpServerDescriptor server) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverId", server.getServerId());
        result.put("name", server.getName());
        result.put("endpoint", server.getEndpoint());
        result.put("transport", server.getTransport());
        result.put("enabled", server.isEnabled());
        result.put("metadata", server.getMetadata());
        result.put("toolCount", registry.listTools(server.getServerId()).size());
        result.put("lastDiscoveredAt", registry.lastDiscoveredAt(server.getServerId()).map(LocalDateTime::toString).orElse(""));
        result.putAll(cacheState(server));
        return result;
    }

    private Map<String, Object> tool(AgentMcpToolDescriptor tool) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverId", tool.getServerId());
        result.put("toolName", tool.getToolName());
        result.put("qualifiedName", tool.qualifiedName());
        result.put("description", tool.getDescription());
        result.put("inputSchema", tool.getInputSchema());
        result.put("enabled", tool.isEnabled());
        result.put("discoveredAt", tool.getDiscoveredAt().toString());
        return result;
    }

    private Map<String, Object> agentTool(AgentMcpToolDescriptor tool) {
        Map<String, Object> result = tool(tool);
        result.put("name", agentToolName(tool));
        result.put("category", "mcp");
        result.put("source", tool.getServerId());
        result.put("description", defaultText(tool.getDescription(),
                "MCP tool " + tool.qualifiedName()));
        return result;
    }

    private String agentToolName(AgentMcpToolDescriptor tool) {
        return "mcp_" + safeToolName(tool.getServerId()) + "__" + safeToolName(tool.getToolName());
    }

    private String safeToolName(String value) {
        String text = text(value).replaceAll("[^A-Za-z0-9_]", "_");
        return StringUtils.hasText(text) ? text : "tool";
    }

    private static boolean isStdioTransport(String transport) {
        return "stdio".equals(Objects.toString(transport, "").trim().toLowerCase(Locale.ROOT));
    }

    private boolean cacheExpired(AgentMcpServerDescriptor server) {
        Object expired = cacheState(server).get("cacheExpired");
        return expired instanceof Boolean value && value;
    }

    private Map<String, Object> cacheState(AgentMcpServerDescriptor server) {
        LocalDateTime discoveredAt = registry.lastDiscoveredAt(server.getServerId()).orElse(null);
        long ttlSeconds = cacheTtlSeconds(server.getMetadata());
        long ageSeconds = discoveredAt == null
                ? 0
                : Math.max(0, Duration.between(discoveredAt, LocalDateTime.now()).toSeconds());
        boolean expired = discoveredAt != null && ttlSeconds > 0 && ageSeconds > ttlSeconds;
        String status = discoveredAt == null
                ? "empty"
                : expired ? "expired" : ttlSeconds > 0 ? "fresh" : "unbounded";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cacheStatus", status);
        result.put("cacheAgeSeconds", ageSeconds);
        result.put("cacheTtlSeconds", ttlSeconds);
        result.put("cacheExpired", expired);
        return result;
    }

    private long cacheTtlSeconds(Map<String, Object> metadata) {
        long primary = number(metadata == null ? null : metadata.get("toolCacheTtlSeconds"), -1);
        if (primary >= 0) {
            return primary;
        }
        return Math.max(0, number(metadata == null ? null : metadata.get("cacheTtlSeconds"), 0));
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
    }

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void loadState() {
        if (stateFile == null || !Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            AgentMcpRegistry.Snapshot snapshot = objectMapper.readValue(stateFile.toFile(), AgentMcpRegistry.Snapshot.class);
            registry.restore(snapshot);
        } catch (IOException | RuntimeException e) {
            throw new AppException("MCP_0301", "mcp admin state load failed: " + e.getMessage(), e);
        }
    }

    private void persistState() {
        if (stateFile == null) {
            return;
        }
        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), registry.snapshot());
        } catch (IOException | RuntimeException e) {
            throw new AppException("MCP_0302", "mcp admin state save failed: " + e.getMessage(), e);
        }
    }

    private static Path resolveStateFile(String stateFile, boolean useDefaultStateFile) {
        String path = stateFile == null ? "" : stateFile.trim();
        if (!StringUtils.hasText(path) && useDefaultStateFile) {
            path = "data/mcp-admin-state.json";
        }
        if (!StringUtils.hasText(path)) {
            return null;
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    interface McpToolDiscoverer {
        List<AgentMcpToolDescriptor> discover(AgentMcpServerDescriptor server, Map<String, Object> request);
    }

    interface McpToolInvoker {
        Map<String, Object> invoke(AgentMcpServerDescriptor server,
                                   AgentMcpToolDescriptor tool,
                                   Map<String, Object> arguments);

        default void invalidate(String serverId) {
        }
    }

    static class SdkMcpToolDiscoverer implements McpToolDiscoverer {

        private static final int MAX_TOOL_PAGES = 50;

        @Override
        public List<AgentMcpToolDescriptor> discover(AgentMcpServerDescriptor server, Map<String, Object> request) {
            if (server == null) {
                throw new AppException("MCP_0203", "mcp server cannot be null");
            }
            Map<String, Object> body = request == null ? Map.of() : request;
            McpSyncClient client = null;
            try {
                client = McpClient.sync(transport(server, body))
                        .requestTimeout(Duration.ofSeconds(number(body.get("timeoutSeconds"),
                                number(server.getMetadata().get("timeoutSeconds"), 120))))
                        .build();
                client.initialize();
                return readTools(server.getServerId(), client);
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                throw new AppException("MCP_0204", "mcp tool discovery failed: " + e.getMessage(), e);
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }

        private McpClientTransport transport(AgentMcpServerDescriptor server, Map<String, Object> body) {
            String transport = text(server.getTransport()).toLowerCase(Locale.ROOT).replace("-", "_");
            Map<String, String> headers = headers(server, body);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            headers.forEach(requestBuilder::header);
            if (isStdioTransport(transport)) {
                return stdioTransport(server, body);
            }
            if ("sse".equals(transport)) {
                SseEndpoint endpoint = sseEndpoint(server.getEndpoint(), body);
                return HttpClientSseClientTransport.builder(endpoint.baseUri())
                        .sseEndpoint(endpoint.path())
                        .requestBuilder(requestBuilder)
                        .build();
            }
            if (!StringUtils.hasText(transport) || "streamable_http".equals(transport)) {
                return HttpClientStreamableHttpTransport.builder(server.getEndpoint())
                        .requestBuilder(requestBuilder)
                        .openConnectionOnStartup(bool(body.get("openConnectionOnStartup"), true))
                        .build();
            }
            throw new AppException("MCP_0205", "unsupported mcp transport for discovery: " + server.getTransport());
        }

        private McpClientTransport stdioTransport(AgentMcpServerDescriptor server, Map<String, Object> body) {
            Map<String, Object> metadata = server.getMetadata();
            String command = defaultText(text(body.get("command")), text(metadata.get("command")));
            if (!StringUtils.hasText(command)) {
                throw new AppException("MCP_0206", "stdio mcp command cannot be blank: " + server.getServerId());
            }
            ServerParameters serverParameters = ServerParameters.builder(command)
                    .args(stringList(body.containsKey("args") ? body.get("args") : metadata.get("args")))
                    .env(stringMap(body.containsKey("env") ? body.get("env") : metadata.get("env")))
                    .build();
            return stdioClientTransport(serverParameters);
        }

        private StdioClientTransport stdioClientTransport(ServerParameters serverParameters) {
            return new StdioClientTransport(serverParameters, McpJsonMapper.createDefault());
        }

        private List<AgentMcpToolDescriptor> readTools(String serverId, McpSyncClient client) {
            List<AgentMcpToolDescriptor> tools = new ArrayList<>();
            String cursor = "";
            for (int page = 0; page < MAX_TOOL_PAGES; page++) {
                McpSchema.ListToolsResult result = StringUtils.hasText(cursor)
                        ? client.listTools(cursor)
                        : client.listTools();
                if (result == null || result.tools() == null || result.tools().isEmpty()) {
                    break;
                }
                for (McpSchema.Tool tool : result.tools()) {
                    tools.add(toTool(serverId, tool));
                }
                cursor = result.nextCursor();
                if (!StringUtils.hasText(cursor)) {
                    break;
                }
            }
            return tools;
        }

        private AgentMcpToolDescriptor toTool(String serverId, McpSchema.Tool tool) {
            return AgentMcpToolDescriptor.builder(serverId, tool.name())
                    .description(defaultText(tool.description(), tool.title()))
                    .inputSchema(jsonSchema(tool.inputSchema()))
                    .enabled(true)
                    .build();
        }

        private Map<String, Object> jsonSchema(McpSchema.JsonSchema schema) {
            if (schema == null) {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", defaultText(schema.type(), "object"));
            result.put("properties", schema.properties() == null ? Map.of() : schema.properties());
            result.put("required", schema.required() == null ? List.of() : schema.required());
            if (schema.additionalProperties() != null) {
                result.put("additionalProperties", schema.additionalProperties());
            }
            if (schema.defs() != null && !schema.defs().isEmpty()) {
                result.put("$defs", schema.defs());
            }
            if (schema.definitions() != null && !schema.definitions().isEmpty()) {
                result.put("definitions", schema.definitions());
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> headers(AgentMcpServerDescriptor server, Map<String, Object> body) {
            Map<String, String> result = new LinkedHashMap<>();
            Object metadataHeaders = server.getMetadata().get("headers");
            if (metadataHeaders instanceof Map<?, ?> headers) {
                headers.forEach((key, value) -> putHeader(result, key, value));
            }
            Object bodyHeaders = body.get("headers");
            if (bodyHeaders instanceof Map<?, ?> headers) {
                headers.forEach((key, value) -> putHeader(result, key, value));
            }
            return result;
        }

        private void putHeader(Map<String, String> result, Object key, Object value) {
            String name = text(key);
            String headerValue = text(value);
            if (StringUtils.hasText(name) && StringUtils.hasText(headerValue)) {
                result.put(name, headerValue);
            }
        }

        private List<String> stringList(Object value) {
            if (value instanceof List<?> list) {
                return list.stream()
                        .map(this::text)
                        .filter(StringUtils::hasText)
                        .toList();
            }
            String text = text(value);
            if (!StringUtils.hasText(text)) {
                return List.of();
            }
            return List.of(text);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> stringMap(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<Object, Object>) map).forEach((key, item) -> {
                String name = text(key);
                String text = text(item);
                if (StringUtils.hasText(name) && StringUtils.hasText(text)) {
                    result.put(name, text);
                }
            });
            return result;
        }

        private SseEndpoint sseEndpoint(String endpoint, Map<String, Object> body) {
            String baseUriOverride = text(body.get("baseUri"));
            String endpointOverride = text(body.get("sseEndpoint"));
            if (StringUtils.hasText(baseUriOverride)) {
                return new SseEndpoint(baseUriOverride, StringUtils.hasText(endpointOverride) ? endpointOverride : "/sse");
            }
            URI uri = URI.create(endpoint);
            String baseUri = uri.getScheme() + "://" + uri.getAuthority();
            String path = StringUtils.hasText(endpointOverride)
                    ? endpointOverride
                    : defaultText(uri.getRawPath(), "/sse");
            if (StringUtils.hasText(uri.getRawQuery())) {
                path = path + "?" + uri.getRawQuery();
            }
            return new SseEndpoint(baseUri, path);
        }

        private long number(Object value, long fallback) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = text(value);
            if (!StringUtils.hasText(text)) {
                return fallback;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private boolean bool(Object value, boolean fallback) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = text(value);
            return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
        }

        private String defaultText(String value, String fallback) {
            return StringUtils.hasText(value) ? value : fallback;
        }

        private String text(Object value) {
            return Objects.toString(value, "").trim();
        }

        private record SseEndpoint(String baseUri, String path) {
        }
    }

    static class SdkMcpToolInvoker implements McpToolInvoker {

        private final ConcurrentMap<String, RuntimeHandle> runtimes = new ConcurrentHashMap<>();

        @Override
        public Map<String, Object> invoke(AgentMcpServerDescriptor server,
                                          AgentMcpToolDescriptor tool,
                                          Map<String, Object> arguments) {
            if (server == null || tool == null) {
                throw new AppException("MCP_0403", "mcp server and tool cannot be null");
            }
            if (isStdioTransport(server.getTransport())) {
                return invokeWithTransientClient(server, tool, arguments);
            }
            RuntimeHandle runtime = null;
            try {
                runtime = runtime(server);
                runtime.lock().lock();
                try {
                    McpSchema.CallToolResult result = runtime.client().callTool(new McpSchema.CallToolRequest(
                            tool.getToolName(),
                            arguments == null ? Map.of() : arguments));
                    return result(tool, result);
                } finally {
                    runtime.lock().unlock();
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                invalidate(server.getServerId());
                throw new AppException("MCP_0402", "mcp tool call failed: " + e.getMessage(), e);
            }
        }

        private Map<String, Object> invokeWithTransientClient(AgentMcpServerDescriptor server,
                                                              AgentMcpToolDescriptor tool,
                                                              Map<String, Object> arguments) {
            McpSyncClient client = null;
            try {
                client = client(server);
                McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                        tool.getToolName(),
                        arguments == null ? Map.of() : arguments));
                return result(tool, result);
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                throw new AppException("MCP_0402", "mcp tool call failed: " + e.getMessage(), e);
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignored) {
                        // A transient stdio client is best-effort cleaned up after each tool call.
                    }
                }
            }
        }

        @Override
        public void invalidate(String serverId) {
            String key = text(serverId);
            if (!StringUtils.hasText(key)) {
                return;
            }
            closeQuietly(runtimes.remove(key));
        }

        private RuntimeHandle runtime(AgentMcpServerDescriptor server) {
            String runtimeKey = runtimeKey(server);
            return runtimes.compute(server.getServerId(), (serverId, current) -> {
                if (current != null && current.runtimeKey().equals(runtimeKey)) {
                    return current;
                }
                RuntimeHandle next = new RuntimeHandle(runtimeKey, client(server), new ReentrantLock());
                closeQuietly(current);
                return next;
            });
        }

        private McpSyncClient client(AgentMcpServerDescriptor server) {
            McpSyncClient client = McpClient.sync(transport(server))
                    .requestTimeout(Duration.ofSeconds(number(server.getMetadata().get("timeoutSeconds"), 120)))
                    .build();
            client.initialize();
            return client;
        }

        private String runtimeKey(AgentMcpServerDescriptor server) {
            return String.join("|",
                    text(server.getServerId()),
                    text(server.getEndpoint()),
                    text(server.getTransport()),
                    String.valueOf(server.getMetadata()));
        }

        private void closeQuietly(RuntimeHandle runtime) {
            if (runtime == null || runtime.client() == null) {
                return;
            }
            try {
                runtime.client().close();
            } catch (Exception ignored) {
                // Closing a stale MCP client must not break the next call path.
            }
        }

        private McpClientTransport transport(AgentMcpServerDescriptor server) {
            String transport = text(server.getTransport()).toLowerCase(Locale.ROOT).replace("-", "_");
            Map<String, String> headers = headers(server);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            headers.forEach(requestBuilder::header);
            if (isStdioTransport(transport)) {
                return stdioTransport(server);
            }
            if ("sse".equals(transport)) {
                SseEndpoint endpoint = sseEndpoint(server.getEndpoint(), Map.of());
                return HttpClientSseClientTransport.builder(endpoint.baseUri())
                        .sseEndpoint(endpoint.path())
                        .requestBuilder(requestBuilder)
                        .build();
            }
            if (!StringUtils.hasText(transport) || "streamable_http".equals(transport)) {
                return HttpClientStreamableHttpTransport.builder(server.getEndpoint())
                        .requestBuilder(requestBuilder)
                        .openConnectionOnStartup(bool(server.getMetadata().get("openConnectionOnStartup"), true))
                        .build();
            }
            throw new AppException("MCP_0205", "unsupported mcp transport for call: " + server.getTransport());
        }

        private McpClientTransport stdioTransport(AgentMcpServerDescriptor server) {
            Map<String, Object> metadata = server.getMetadata();
            String command = text(metadata.get("command"));
            if (!StringUtils.hasText(command)) {
                throw new AppException("MCP_0206", "stdio mcp command cannot be blank: " + server.getServerId());
            }
            ServerParameters serverParameters = ServerParameters.builder(command)
                    .args(stringList(metadata.get("args")))
                    .env(stringMap(metadata.get("env")))
                    .build();
            return stdioClientTransport(serverParameters);
        }

        private StdioClientTransport stdioClientTransport(ServerParameters serverParameters) {
            return new StdioClientTransport(serverParameters, McpJsonMapper.createDefault());
        }

        private Map<String, Object> result(AgentMcpToolDescriptor tool, McpSchema.CallToolResult result) {
            if (result == null) {
                return Map.of("qualifiedName", tool.qualifiedName(), "isError", true, "text", "");
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("qualifiedName", tool.qualifiedName());
            output.put("isError", Boolean.TRUE.equals(result.isError()));
            output.put("text", extractTextContent(result.content()));
            output.put("structuredContent", result.structuredContent() == null ? Map.of() : result.structuredContent());
            output.put("content", result.content() == null
                    ? List.of()
                    : result.content().stream().map(String::valueOf).toList());
            return output;
        }

        private String extractTextContent(List<McpSchema.Content> contents) {
            if (contents == null || contents.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (McpSchema.Content content : contents) {
                if (content instanceof McpSchema.TextContent textContent && StringUtils.hasText(textContent.text())) {
                    if (!text.isEmpty()) {
                        text.append(System.lineSeparator());
                    }
                    text.append(textContent.text());
                }
            }
            return text.toString();
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> headers(AgentMcpServerDescriptor server) {
            Map<String, String> result = new LinkedHashMap<>();
            Object metadataHeaders = server.getMetadata().get("headers");
            if (metadataHeaders instanceof Map<?, ?> headers) {
                headers.forEach((key, value) -> putHeader(result, key, value));
            }
            return result;
        }

        private void putHeader(Map<String, String> result, Object key, Object value) {
            String name = text(key);
            String headerValue = text(value);
            if (StringUtils.hasText(name) && StringUtils.hasText(headerValue)) {
                result.put(name, headerValue);
            }
        }

        private List<String> stringList(Object value) {
            if (value instanceof List<?> list) {
                return list.stream()
                        .map(this::text)
                        .filter(StringUtils::hasText)
                        .toList();
            }
            String text = text(value);
            if (!StringUtils.hasText(text)) {
                return List.of();
            }
            return List.of(text);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> stringMap(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<Object, Object>) map).forEach((key, item) -> {
                String name = text(key);
                String text = text(item);
                if (StringUtils.hasText(name) && StringUtils.hasText(text)) {
                    result.put(name, text);
                }
            });
            return result;
        }

        private SseEndpoint sseEndpoint(String endpoint, Map<String, Object> body) {
            String baseUriOverride = text(body.get("baseUri"));
            String endpointOverride = text(body.get("sseEndpoint"));
            if (StringUtils.hasText(baseUriOverride)) {
                return new SseEndpoint(baseUriOverride, StringUtils.hasText(endpointOverride) ? endpointOverride : "/sse");
            }
            URI uri = URI.create(endpoint);
            String baseUri = uri.getScheme() + "://" + uri.getAuthority();
            String path = StringUtils.hasText(endpointOverride)
                    ? endpointOverride
                    : defaultText(uri.getRawPath(), "/sse");
            if (StringUtils.hasText(uri.getRawQuery())) {
                path = path + "?" + uri.getRawQuery();
            }
            return new SseEndpoint(baseUri, path);
        }

        private long number(Object value, long fallback) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = text(value);
            if (!StringUtils.hasText(text)) {
                return fallback;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private boolean bool(Object value, boolean fallback) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = text(value);
            return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
        }

        private String defaultText(String value, String fallback) {
            return StringUtils.hasText(value) ? value : fallback;
        }

        private String text(Object value) {
            return Objects.toString(value, "").trim();
        }

        private record SseEndpoint(String baseUri, String path) {
        }

        private record RuntimeHandle(String runtimeKey, McpSyncClient client, ReentrantLock lock) {
        }
    }
}















