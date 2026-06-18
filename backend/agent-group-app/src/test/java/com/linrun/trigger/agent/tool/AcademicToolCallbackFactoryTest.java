package com.linrun.trigger.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollection;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollectionFactory;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.trigger.http.agent.McpAdminHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicToolCallbackFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConvertAcademicToolCollectionToSpringAiCallbacks() throws Exception {
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.register(AcademicToolDefinition.builder("echo_tool")
                .description("Echo text.")
                .category("test")
                .source("unit-test")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of("type", "string", "description", "Text to echo.")),
                        "required", List.of("text")))
                .requiredArguments(List.of("text"))
                .build(), command -> Map.of(
                "text", command.getArguments().get("text"),
                "userId", command.getUserId(),
                "sessionId", command.getSessionId()));
        AcademicToolCollection collection = new AcademicToolCollectionFactory(registry).buildAll("unit");

        ToolCallback[] callbacks = AcademicToolCallbackFactory.createCallbacks(
                objectMapper, collection, "U1001", "S1001");

        assertEquals(1, callbacks.length);
        assertEquals("echo_tool", callbacks[0].getToolDefinition().name());
        assertTrue(callbacks[0].getToolDefinition().inputSchema().contains("\"text\""));

        String raw = callbacks[0].call("{\"text\":\"hello\"}");
        JsonNode root = objectMapper.readTree(raw);

        assertTrue(root.path("success").asBoolean());
        assertEquals("hello", root.path("result").path("text").asText());
        assertEquals("U1001", root.path("result").path("userId").asText());
        assertEquals("S1001", root.path("result").path("sessionId").asText());
    }

    @Test
    void shouldPreviewToolDefinitionsWithoutCallingTools() {
        AcademicToolCallbackFactory factory = new AcademicToolCallbackFactory(
                objectMapper, null, null, null, null, null, null, null, null, null, null, null, null, null);

        List<Map<String, Object>> offlineTools = factory.preview("capabilities", false);
        List<String> offlineToolNames = offlineTools.stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();

        assertTrue(offlineToolNames.contains(AcademicToolOutputNames.DATA_ANALYSIS));
        assertTrue(offlineToolNames.contains(AcademicToolOutputNames.REPORT_TOOL));
        assertTrue(offlineToolNames.contains(AcademicToolOutputNames.PLANNING));
        assertFalse(offlineToolNames.contains(AcademicToolOutputNames.WEB_FETCH));
        assertTrue(offlineTools.get(0).containsKey("requiredArguments"));

        List<String> onlineToolNames = factory.preview("capabilities", true).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();

        assertTrue(onlineToolNames.contains(AcademicToolOutputNames.WEB_FETCH));
    }

    @Test
    void shouldExposeCachedMcpToolsAsAgentCallbacks() throws Exception {
        McpAdminHandler mcpAdminHandler = new McpAdminHandler() {
            @Override
            public List<Map<String, Object>> listAgentToolDefinitions() {
                return List.of(Map.of(
                        "name", "mcp_data_source__render_chart",
                        "description", "Render chart from query.",
                        "category", "mcp",
                        "source", "data-source",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of("query", Map.of("type", "string")),
                                "required", List.of("query"))));
            }

            @Override
            public Map<String, Object> callAgentTool(String agentToolName, Map<String, Object> arguments) {
                return Map.of(
                        "qualifiedName", "data-source.render.chart",
                        "text", "chart for " + arguments.get("query"),
                        "isError", false);
            }
        };

        AcademicToolCallbackFactory factory = new AcademicToolCallbackFactory(
                objectMapper, null, null, null, null, null, null, null, null, null, null, null, null,
                provider(mcpAdminHandler));

        List<Map<String, Object>> tools = factory.preview("capabilities", false);
        assertTrue(tools.stream().anyMatch(tool -> "mcp_data_source__render_chart".equals(tool.get("name"))));

        ToolCallback callback = List.of(factory.create("data", "U1002", "S1002", false)).stream()
                .filter(tool -> "mcp_data_source__render_chart".equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
        String raw = callback.call("{\"query\":\"sales\"}");
        JsonNode root = objectMapper.readTree(raw);

        assertTrue(root.path("success").asBoolean());
        assertEquals("data-source.render.chart", root.path("result").path("qualifiedName").asText());
        assertEquals("chart for sales", root.path("result").path("text").asText());
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }
        };
    }
}















