package com.linrun.trigger.http;

import com.linrun.domain.academic.runtime.tool.mcp.AcademicMcpToolDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpAdminControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new McpAdminController(new McpAdminHandler()))
            .build();

    @Test
    void shouldRegisterAndListMcpServer() throws Exception {
        mockMvc.perform(post("/api/v1/mcp/admin/servers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "serverId": "research",
                                  "endpoint": "http://localhost:8090/mcp"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.serverId").value("research"));

        mockMvc.perform(get("/api/v1/mcp/admin/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serverId").value("research"));
    }

    @Test
    void shouldCacheAndListDiscoveredTools() throws Exception {
        mockMvc.perform(post("/api/v1/mcp/admin/servers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "serverId": "research",
                                  "endpoint": "http://localhost:8090/mcp"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/mcp/admin/servers/research/tools/cache")
                        .contentType("application/json")
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "name": "web_fetch",
                                      "description": "fetch web page"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCount").value(1));

        mockMvc.perform(get("/api/v1/mcp/admin/tools?enabledOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].qualifiedName").value("research.web_fetch"));
    }

    @Test
    void shouldDiscoverToolsFromRegisteredServer() throws Exception {
        McpAdminHandler handler = new McpAdminHandler((server, request) -> List.of(
                AcademicMcpToolDescriptor.builder(server.getServerId(), "data_analysis")
                        .description("analyze data")
                        .inputSchema(Map.of("type", "object"))
                        .build()));
        MockMvc localMockMvc = MockMvcBuilders
                .standaloneSetup(new McpAdminController(handler))
                .build();

        localMockMvc.perform(post("/api/v1/mcp/admin/servers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "serverId": "data",
                                  "endpoint": "http://localhost:8093/mcp"
                                }
                                """))
                .andExpect(status().isOk());

        localMockMvc.perform(post("/api/v1/mcp/admin/servers/data/tools/discover")
                        .contentType("application/json")
                        .content("""
                                {
                                  "cache": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCount").value(1))
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.tools[0].qualifiedName").value("data.data_analysis"));

        localMockMvc.perform(get("/api/v1/mcp/admin/tools?enabledOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].qualifiedName").value("data.data_analysis"));
    }

    @Test
    void shouldExposeHealthExportImportAndToolCall() throws Exception {
        McpAdminHandler handler = new McpAdminHandler(
                (server, request) -> List.of(),
                (server, tool, arguments) -> Map.of(
                        "qualifiedName", tool.qualifiedName(),
                        "text", "called " + arguments.get("query"),
                        "isError", false));
        MockMvc localMockMvc = MockMvcBuilders
                .standaloneSetup(new McpAdminController(handler))
                .build();

        localMockMvc.perform(post("/api/v1/mcp/admin/import")
                        .contentType("application/json")
                        .content("""
                                {
                                  "replace": true,
                                  "snapshot": {
                                    "servers": [
                                      {
                                        "serverId": "research",
                                        "name": "research",
                                        "endpoint": "http://localhost:8090/mcp",
                                        "transport": "streamable_http",
                                        "enabled": true,
                                        "metadata": {}
                                      }
                                    ],
                                    "toolsByServer": {
                                      "research": [
                                        {
                                          "serverId": "research",
                                          "toolName": "web_fetch",
                                          "description": "fetch web page",
                                          "inputSchema": { "type": "object" },
                                          "enabled": true,
                                          "discoveredAt": "2026-06-05T10:00:00"
                                        }
                                      ]
                                    },
                                    "discoveredAtByServer": {
                                      "research": "2026-06-05T10:00:00"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverCount").value(1))
                .andExpect(jsonPath("$.data.toolCount").value(1));

        localMockMvc.perform(get("/api/v1/mcp/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("ready"));

        localMockMvc.perform(get("/api/v1/mcp/admin/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverCount").value(1))
                .andExpect(jsonPath("$.data.toolCount").value(1));

        localMockMvc.perform(post("/api/v1/mcp/admin/tools/research.web_fetch/call")
                        .contentType("application/json")
                        .content("""
                                {
                                  "arguments": {
                                    "query": "agent"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualifiedName").value("research.web_fetch"))
                .andExpect(jsonPath("$.data.text").value("called agent"));
    }
}
