package com.linrun.trigger.http.agent;

import com.linrun.types.exception.AppException;
import com.linrun.trigger.config.McpAdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpAdminHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRegisterServerCacheToolsAndListEnabledTools() {
        McpAdminHandler handler = new McpAdminHandler();
        handler.registerServer(Map.of(
                "serverId", "research",
                "name", "research tools",
                "endpoint", "http://localhost:8090/mcp"));

        Map<String, Object> cacheResult = handler.cacheTools("research", Map.of("tools", List.of(
                Map.of(
                        "name", "web_fetch",
                        "description", "fetch web page",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of("url", Map.of("type", "string")),
                                "required", List.of("url"))))));

        assertEquals(1, cacheResult.get("toolCount"));
        assertEquals("research.web_fetch", handler.listTools("", true).getFirst().get("qualifiedName"));
        assertEquals(1, handler.listServers().getFirst().get("toolCount"));
    }

    @Test
    void shouldHideEnabledToolsWhenServerDisabled() {
        McpAdminHandler handler = new McpAdminHandler();
        handler.registerServer(Map.of("serverId", "image", "endpoint", "http://localhost:8091/mcp"));
        handler.cacheTools("image", Map.of("tools", List.of(Map.of("toolName", "generate"))));

        handler.enableServer("image", false);

        assertTrue(handler.listTools("", true).isEmpty());
        assertEquals(false, handler.listServers().getFirst().get("enabled"));
    }

    @Test
    void shouldRejectBlankToolName() {
        McpAdminHandler handler = new McpAdminHandler();
        handler.registerServer(Map.of("serverId", "bad", "endpoint", "http://localhost:8092/mcp"));

        AppException exception = assertThrows(AppException.class,
                () -> handler.cacheTools("bad", Map.of("tools", List.of(Map.of("description", "missing name")))));

        assertEquals("MCP_0202", exception.getCode());
    }

    @Test
    void shouldDiscoverToolsAndCacheByDefault() {
        McpAdminHandler handler = new McpAdminHandler((server, request) -> List.of(
                com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpToolDescriptor.builder(
                                server.getServerId(), "chart")
                        .description("render chart")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of("query", Map.of("type", "string")),
                                "required", List.of("query")))
                        .build()));
        handler.registerServer(Map.of("serverId", "data", "endpoint", "http://localhost:8093/mcp"));

        Map<String, Object> result = handler.discoverTools("data", Map.of());

        assertEquals(1, result.get("toolCount"));
        assertEquals(true, result.get("cached"));
        assertEquals("data.chart", handler.listTools("", true).getFirst().get("qualifiedName"));
        assertEquals(1, handler.listServers().getFirst().get("toolCount"));
    }

    @Test
    void shouldDiscoverToolsWithoutCacheWhenRequested() {
        McpAdminHandler handler = new McpAdminHandler((server, request) -> List.of(
                com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpToolDescriptor.builder(
                                server.getServerId(), "preview")
                        .description("preview only")
                        .build()));
        handler.registerServer(Map.of("serverId", "preview", "endpoint", "http://localhost:8094/mcp"));

        Map<String, Object> result = handler.discoverTools("preview", Map.of("cache", false));

        assertEquals(1, result.get("toolCount"));
        assertEquals(false, result.get("cached"));
        assertTrue(handler.listTools("", true).isEmpty());
    }

    @Test
    void shouldPersistAndReloadAdminState() {
        Path stateFile = tempDir.resolve("mcp-state.json");
        McpAdminHandler first = new McpAdminHandler(null, stateFile);
        first.registerServer(Map.of(
                "serverId", "report",
                "name", "report tools",
                "endpoint", "http://localhost:8095/mcp",
                "transport", "sse"));
        first.cacheTools("report", Map.of("tools", List.of(Map.of(
                "toolName", "write_report",
                "description", "write structured report",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("topic", Map.of("type", "string")),
                        "required", List.of("topic"))))));
        first.enableServer("report", false);

        McpAdminHandler second = new McpAdminHandler(null, stateFile);

        assertTrue(Files.isRegularFile(stateFile));
        assertEquals(1, second.listServers().size());
        assertEquals(false, second.listServers().getFirst().get("enabled"));
        assertEquals("sse", second.listServers().getFirst().get("transport"));
        assertEquals("report.write_report", second.listTools("", false).getFirst().get("qualifiedName"));
        assertTrue(second.listTools("", true).isEmpty());
    }

    @Test
    void shouldExportImportAndReportHealth() {
        McpAdminHandler source = new McpAdminHandler();
        source.registerServer(Map.of("serverId", "research", "endpoint", "http://localhost:8090/mcp"));
        source.cacheTools("research", Map.of("tools", List.of(Map.of(
                "toolName", "web_fetch",
                "description", "fetch web page"))));

        McpAdminHandler target = new McpAdminHandler();
        target.registerServer(Map.of("serverId", "local", "endpoint", "http://localhost:8091/mcp"));
        Map<String, Object> imported = target.importState(source.exportState());
        Map<String, Object> health = target.health();

        assertEquals(false, imported.get("replace"));
        assertEquals(2, imported.get("serverCount"));
        assertEquals(1, imported.get("toolCount"));
        assertEquals("degraded", health.get("overallStatus"));
        assertEquals(1L, health.get("readyServerCount"));
        assertEquals(1L, health.get("degradedServerCount"));
        Map<String, Object> summary = (Map<String, Object>) health.get("registrySummary");
        assertEquals(2, summary.get("serverCount"));
        assertEquals(2, summary.get("enabledServerCount"));
        assertEquals(1, summary.get("registeredToolCount"));
        assertEquals(1, summary.get("enabledToolCount"));
        assertEquals(1, summary.get("cachedServerCount"));
        assertEquals(1, summary.get("emptyCacheServerCount"));
        assertEquals(List.of("local"), summary.get("enabledServersWithoutCachedTools"));
        assertEquals(true, summary.get("hasEnabledServerWithoutCache"));
        assertEquals(Map.of("streamable_http", 2), summary.get("transportCounts"));
        assertEquals("research.web_fetch", target.listTools("research", true).getFirst().get("qualifiedName"));
    }

    @Test
    void shouldReportExpiredToolCacheInHealth() {
        McpAdminHandler handler = new McpAdminHandler();
        String staleDiscoveredAt = LocalDateTime.now().minusSeconds(120).toString();

        handler.importState(Map.of(
                "replace", true,
                "snapshot", Map.of(
                        "servers", List.of(Map.of(
                                "serverId", "stale",
                                "name", "stale server",
                                "endpoint", "http://localhost:8099/mcp",
                                "transport", "streamable_http",
                                "enabled", true,
                                "metadata", Map.of("toolCacheTtlSeconds", 60))),
                        "toolsByServer", Map.of("stale", List.of(Map.of(
                                "serverId", "stale",
                                "toolName", "search",
                                "description", "search stale cache",
                                "inputSchema", Map.of(),
                                "enabled", true,
                                "discoveredAt", staleDiscoveredAt))),
                        "discoveredAtByServer", Map.of("stale", staleDiscoveredAt))));

        Map<String, Object> health = handler.health();
        Map<String, Object> server = ((List<Map<String, Object>>) health.get("servers")).getFirst();

        assertEquals("degraded", health.get("overallStatus"));
        assertEquals("degraded", server.get("status"));
        assertEquals("expired", server.get("cacheStatus"));
        assertEquals(true, server.get("cacheExpired"));
        assertEquals(60L, server.get("cacheTtlSeconds"));
        assertTrue((Long) server.get("cacheAgeSeconds") >= 60L);
        assertEquals(true, handler.listServers().getFirst().get("cacheExpired"));
        assertEquals("stale.search", handler.listTools("", true).getFirst().get("qualifiedName"));
        assertTrue(handler.listAgentToolDefinitions().isEmpty());

        AppException agentCallException = assertThrows(AppException.class,
                () -> handler.callAgentTool("mcp_stale__search", Map.of()));
        AppException registeredCallException = assertThrows(AppException.class,
                () -> handler.callRegisteredTool("stale.search", Map.of()));

        assertEquals("MCP_0404", agentCallException.getCode());
        assertEquals("MCP_0404", registeredCallException.getCode());
    }

    @Test
    void shouldExposeAndCallAgentSafeMcpToolName() {
        McpAdminHandler handler = new McpAdminHandler(
                (server, request) -> List.of(),
                (server, tool, arguments) -> Map.of(
                        "qualifiedName", tool.qualifiedName(),
                        "text", "chart for " + arguments.get("query"),
                        "isError", false));
        handler.registerServer(Map.of("serverId", "data-source", "endpoint", "http://localhost:8096/mcp"));
        handler.cacheTools("data-source", Map.of("tools", List.of(Map.of(
                "toolName", "render.chart",
                "description", "render chart",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query"))))));

        Map<String, Object> definition = handler.listAgentToolDefinitions().getFirst();
        Map<String, Object> result = handler.callAgentTool("mcp_data_source__render_chart", Map.of("query", "sales"));

        assertEquals("mcp_data_source__render_chart", definition.get("name"));
        assertEquals("mcp", definition.get("category"));
        assertEquals("data-source", definition.get("source"));
        assertEquals("data-source.render.chart", result.get("qualifiedName"));
        assertEquals("chart for sales", result.get("text"));
    }

    @Test
    void shouldCallRegisteredToolByQualifiedName() {
        McpAdminHandler handler = new McpAdminHandler(
                (server, request) -> List.of(),
                (server, tool, arguments) -> Map.of(
                        "qualifiedName", tool.qualifiedName(),
                        "text", "ok " + arguments.get("topic"),
                        "isError", false));
        handler.registerServer(Map.of("serverId", "report", "endpoint", "http://localhost:8098/mcp"));
        handler.cacheTools("report", Map.of("tools", List.of(Map.of("toolName", "write"))));

        Map<String, Object> result = handler.callRegisteredTool(
                "report.write",
                Map.of("arguments", Map.of("topic", "quota")));

        assertEquals("report.write", result.get("qualifiedName"));
        assertEquals("ok quota", result.get("text"));
    }


    @Test
    void shouldInvalidateRuntimeWhenServerConfigChanges() {
        RecordingMcpToolInvoker invoker = new RecordingMcpToolInvoker();
        McpAdminHandler handler = new McpAdminHandler((server, request) -> List.of(), invoker);

        handler.registerServer(Map.of("serverId", "runtime", "endpoint", "http://localhost:8097/mcp"));
        handler.registerServer(Map.of("serverId", "runtime", "endpoint", "http://localhost:8098/mcp"));
        handler.enableServer("runtime", false);

        assertEquals(List.of("runtime", "runtime", "runtime"), invoker.invalidated);
    }

    @Test
    void shouldImportConfiguredServersAndTools() {
        Path stateFile = tempDir.resolve("configured-mcp-state.json");
        McpAdminProperties properties = new McpAdminProperties();
        properties.setServers(List.of(server("configured", "http://localhost:8100/mcp", "streamable_http",
                List.of(tool("chart", "render chart")), false)));

        McpAdminHandler handler = new McpAdminHandler((server, request) -> List.of(), new RecordingMcpToolInvoker(),
                stateFile, properties);

        assertEquals(1, handler.listServers().size());
        assertEquals("configured", handler.listServers().getFirst().get("serverId"));
        assertEquals("configured.chart", handler.listTools("", true).getFirst().get("qualifiedName"));
        assertTrue(Files.isRegularFile(stateFile));
    }

    @Test
    void shouldDiscoverConfiguredToolsOnStartup() {
        McpAdminProperties properties = new McpAdminProperties();
        McpAdminProperties.Server server = server("startup", "http://localhost:8101/mcp",
                "streamable_http", List.of(), true);
        properties.setPersistImportedState(false);
        properties.setServers(List.of(server));

        McpAdminHandler handler = new McpAdminHandler((registeredServer, request) -> List.of(
                com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpToolDescriptor.builder(
                                registeredServer.getServerId(), "search")
                        .description("search from startup config")
                        .build()), new RecordingMcpToolInvoker(), null, properties);

        assertEquals("startup.search", handler.listTools("", true).getFirst().get("qualifiedName"));
    }

    @Test
    void shouldImportConfiguredStdioServerWithoutEndpoint() {
        McpAdminProperties properties = new McpAdminProperties();
        McpAdminProperties.Server server = server("local-stdio", "", "stdio",
                List.of(tool("local_tool", "local stdio tool")), false);
        server.setMetadata(Map.of("command", "npx", "args", List.of("-y", "@demo/mcp")));
        properties.setPersistImportedState(false);
        properties.setServers(List.of(server));

        McpAdminHandler handler = new McpAdminHandler((registeredServer, request) -> List.of(),
                new RecordingMcpToolInvoker(), null, properties);

        assertEquals("stdio://local-stdio", handler.listServers().getFirst().get("endpoint"));
        assertEquals("stdio", handler.listServers().getFirst().get("transport"));
        assertEquals("local-stdio.local_tool", handler.listTools("", true).getFirst().get("qualifiedName"));
    }

    @Test
    void shouldRejectStdioDiscoveryWhenCommandMissing() {
        McpAdminHandler handler = new McpAdminHandler(new McpAdminHandler.SdkMcpToolDiscoverer());
        handler.registerServer(Map.of("serverId", "stdio", "transport", "stdio"));

        AppException exception = assertThrows(AppException.class,
                () -> handler.discoverTools("stdio", Map.of()));

        assertEquals("MCP_0206", exception.getCode());
    }

    private McpAdminProperties.Server server(String serverId,
                                             String endpoint,
                                             String transport,
                                             List<McpAdminProperties.Tool> tools,
                                             boolean discoverOnStartup) {
        McpAdminProperties.Server server = new McpAdminProperties.Server();
        server.setServerId(serverId);
        server.setName(serverId + " server");
        server.setEndpoint(endpoint);
        server.setTransport(transport);
        server.setTools(tools);
        server.setDiscoverOnStartup(discoverOnStartup);
        return server;
    }

    private McpAdminProperties.Tool tool(String toolName, String description) {
        McpAdminProperties.Tool tool = new McpAdminProperties.Tool();
        tool.setToolName(toolName);
        tool.setDescription(description);
        tool.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")));
        return tool;
    }

    private static final class RecordingMcpToolInvoker implements McpAdminHandler.McpToolInvoker {

        private final List<String> invalidated = new ArrayList<>();

        @Override
        public Map<String, Object> invoke(com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpServerDescriptor server,
                                          com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpToolDescriptor tool,
                                          Map<String, Object> arguments) {
            return Map.of("qualifiedName", tool.qualifiedName(), "isError", false);
        }

        @Override
        public void invalidate(String serverId) {
            invalidated.add(serverId);
        }
    }
}
