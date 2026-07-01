package com.linrun.trigger.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolCollection;
import com.linrun.domain.agent.runtime.tool.AgentToolCollectionFactory;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.common.AgentCommonToolRuntimeFactory;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDataAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentReportPort;
import com.linrun.domain.agent.runtime.tool.port.AgentScriptRunnerPort;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.domain.agent.runtime.tool.port.AgentWebFetchPort;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import com.linrun.trigger.http.agent.McpAdminHandler;
import com.linrun.types.exception.AppException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class AgentToolCallbackFactory {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AgentCodeInterpreterPort> codeInterpreterPort;
    private final ObjectProvider<AgentWebFetchPort> webFetchPort;
    private final ObjectProvider<AgentDataAnalysisPort> dataAnalysisPort;
    private final ObjectProvider<AgentReportPort> reportPort;
    private final ObjectProvider<AgentImageGenerationPort> imageGenerationPort;
    private final ObjectProvider<AgentMultimodalAnalysisPort> multimodalAnalysisPort;
    private final ObjectProvider<AgentDeepSearchPort> deepSearchPort;
    private final ObjectProvider<AgentFileToolPort> fileToolPort;
    private final ObjectProvider<AgentScriptRunnerPort> scriptRunnerPort;
    private final ObjectProvider<AgentTableRagPort> tableRagPort;
    private final ObjectProvider<AgentNl2SqlPort> nl2SqlPort;
    private final ObjectProvider<TradeConsistencyCheckService> tradeConsistencyCheckService;
    private final ObjectProvider<McpAdminHandler> mcpAdminHandler;

    public AgentToolCallbackFactory(ObjectMapper objectMapper,
                                       ObjectProvider<AgentCodeInterpreterPort> codeInterpreterPort,
                                       ObjectProvider<AgentWebFetchPort> webFetchPort,
                                       ObjectProvider<AgentDataAnalysisPort> dataAnalysisPort,
                                       ObjectProvider<AgentReportPort> reportPort,
                                       ObjectProvider<AgentImageGenerationPort> imageGenerationPort,
                                       ObjectProvider<AgentMultimodalAnalysisPort> multimodalAnalysisPort,
                                       ObjectProvider<AgentDeepSearchPort> deepSearchPort,
                                       ObjectProvider<AgentFileToolPort> fileToolPort,
                                       ObjectProvider<AgentScriptRunnerPort> scriptRunnerPort,
                                       ObjectProvider<AgentTableRagPort> tableRagPort,
                                       ObjectProvider<AgentNl2SqlPort> nl2SqlPort,
                                       ObjectProvider<TradeConsistencyCheckService> tradeConsistencyCheckService,
                                       ObjectProvider<McpAdminHandler> mcpAdminHandler) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.codeInterpreterPort = codeInterpreterPort;
        this.webFetchPort = webFetchPort;
        this.dataAnalysisPort = dataAnalysisPort;
        this.reportPort = reportPort;
        this.imageGenerationPort = imageGenerationPort;
        this.multimodalAnalysisPort = multimodalAnalysisPort;
        this.deepSearchPort = deepSearchPort;
        this.fileToolPort = fileToolPort;
        this.scriptRunnerPort = scriptRunnerPort;
        this.tableRagPort = tableRagPort;
        this.nl2SqlPort = nl2SqlPort;
        this.tradeConsistencyCheckService = tradeConsistencyCheckService;
        this.mcpAdminHandler = mcpAdminHandler;
    }

    public ToolCallback[] create(String scene,
                                 String userId,
                                 String sessionId,
                                 boolean webAccessEnabled) {
        AgentToolCollection collection = createCollection(scene, webAccessEnabled);
        return createCallbacks(objectMapper, collection, userId, sessionId);
    }

    public List<Map<String, Object>> preview(String scene, boolean webAccessEnabled) {
        return createCollection(scene, webAccessEnabled).listDefinitions().stream()
                .map(AgentToolCallbackFactory::previewDefinition)
                .toList();
    }

    private AgentToolCollection createCollection(String scene, boolean webAccessEnabled) {
        AgentToolRuntimeRegistry registry = AgentCommonToolRuntimeFactory.builder()
                .codeInterpreterPort(available(codeInterpreterPort))
                .webFetchPort(available(webFetchPort))
                .dataAnalysisPort(available(dataAnalysisPort))
                .reportPort(available(reportPort))
                .imageGenerationPort(available(imageGenerationPort))
                .multimodalAnalysisPort(available(multimodalAnalysisPort))
                .deepSearchPort(available(deepSearchPort))
                .fileToolPort(available(fileToolPort))
                .scriptRunnerPort(available(scriptRunnerPort))
                .tableRagPort(available(tableRagPort))
                .nl2SqlPort(available(nl2SqlPort))
                .tradeConsistencyCheckService(available(tradeConsistencyCheckService))
                .build()
                .buildRegistry();
        registerMcpTools(registry);
        return new AgentToolCollectionFactory(registry)
                .buildByCategories(scene, categories(scene, webAccessEnabled));
    }

    private void registerMcpTools(AgentToolRuntimeRegistry registry) {
        McpAdminHandler handler = available(mcpAdminHandler);
        if (handler == null) {
            return;
        }
        for (Map<String, Object> definition : handler.listAgentToolDefinitions()) {
            AgentToolDefinition toolDefinition = AgentToolDefinition.fromMcpDefinition(
                    definition,
                    String.valueOf(definition.getOrDefault("category", "mcp")),
                    String.valueOf(definition.getOrDefault("source", "mcp")));
            registry.register(toolDefinition, command -> handler.callAgentTool(
                    command.getToolName(),
                    command.getArguments()));
        }
    }

    public static ToolCallback[] createCallbacks(ObjectMapper objectMapper,
                                                 AgentToolCollection collection,
                                                 String userId,
                                                 String sessionId) {
        if (collection == null) {
            return new ToolCallback[0];
        }
        ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        return collection.listDefinitions().stream()
                .map(definition -> createCallback(mapper, collection, definition, userId, sessionId))
                .toArray(ToolCallback[]::new);
    }

    private static ToolCallback createCallback(ObjectMapper objectMapper,
                                               AgentToolCollection collection,
                                               AgentToolDefinition definition,
                                               String userId,
                                               String sessionId) {
        Function<Map<String, Object>, String> function = arguments ->
                callTool(objectMapper, collection, definition, arguments, userId, sessionId);
        return FunctionToolCallback.<Map<String, Object>, String>builder(definition.getName(), function)
                .description(description(definition))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .inputSchema(json(objectMapper, definition.getInputSchema()))
                .build();
    }

    private static String callTool(ObjectMapper objectMapper,
                                   AgentToolCollection collection,
                                   AgentToolDefinition definition,
                                   Map<String, Object> arguments,
                                   String userId,
                                   String sessionId) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        AgentToolCallCommand command = AgentToolCallCommand.builder(definition.getName())
                .action("spring-ai/tool-call")
                .requestId(firstText(safeArguments.get("requestId"), "tool-" + UUID.randomUUID()))
                .sessionId(sessionId)
                .userId(userId)
                .arguments(safeArguments)
                .build();
        try {
            AgentToolCallResult result = collection.call(command);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("toolName", result.getToolName());
            response.put("action", result.getAction());
            response.put("latencyMillis", result.getLatencyMillis());
            response.put("artifactIds", result.getArtifactIds());
            if (result.isSuccess()) {
                response.put("result", result.getResult());
            } else {
                response.put("errorCode", result.getErrorCode());
                response.put("errorMessage", result.getErrorMessage());
            }
            return json(objectMapper, response);
        } catch (AppException e) {
            return errorJson(objectMapper, definition.getName(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            return errorJson(objectMapper, definition.getName(), "TOOL_CALLBACK_FAILED", e.getMessage());
        }
    }

    private static String errorJson(ObjectMapper objectMapper, String toolName, String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("toolName", toolName);
        response.put("errorCode", code == null ? "" : code);
        response.put("errorMessage", message == null ? "" : message);
        return json(objectMapper, response);
    }

    private static String description(AgentToolDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getDescription());
        builder.append("\n\n类别：").append(definition.getCategory());
        builder.append("；来源：").append(definition.getSource()).append("。");
        builder.append("\n返回值是 JSON，包含 success、result、artifactIds 和错误信息。");
        return builder.toString();
    }

    private static Map<String, Object> previewDefinition(AgentToolDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", definition.getName());
        result.put("description", definition.getDescription());
        result.put("category", definition.getCategory());
        result.put("source", definition.getSource());
        result.put("requiredArguments", definition.getRequiredArguments());
        result.put("inputFields", inputFields(definition.getInputSchema()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> inputFields(Map<String, Object> inputSchema) {
        if (inputSchema == null || !(inputSchema.get("properties") instanceof Map<?, ?> properties)) {
            return List.of();
        }
        return properties.keySet().stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static List<String> categories(String scene, boolean webAccessEnabled) {
        if (isTradeOnlyScene(scene)) {
            return List.of("planning", "report", "trade");
        }
        List<String> categories = new java.util.ArrayList<>(List.of(
                "analysis", "report", "planning", "code", "image",
                "multimodal", "file", "skill", "data", "mcp"));
        if (isTradeScene(scene)) {
            categories.add("trade");
        }
        if (webAccessEnabled) {
            categories.add("web");
            categories.add("search");
        }
        return categories;
    }

    private static boolean isTradeOnlyScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return false;
        }
        String normalized = scene.trim().toLowerCase();
        return normalized.equals("trade-diagnosis")
                || normalized.equals("trade-diagnosis-workspace")
                || normalized.equals("workspace-trade-diagnosis")
                || normalized.equals("workspace-trade")
                || normalized.equals("trade")
                || normalized.equals("trade-flow")
                || normalized.equals("group-trade");
    }

    private static boolean isTradeScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return false;
        }
        String normalized = scene.trim().toLowerCase();
        return normalized.equals("trade-diagnosis")
                || normalized.equals("trade-diagnosis-workspace")
                || normalized.equals("workspace-trade-diagnosis")
                || normalized.equals("workspace-trade")
                || normalized.equals("trade")
                || normalized.equals("trade-flow")
                || normalized.equals("group-trade")
                || normalized.equals("capabilities");
    }

    private static <T> T available(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }

    private static String json(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }
}












