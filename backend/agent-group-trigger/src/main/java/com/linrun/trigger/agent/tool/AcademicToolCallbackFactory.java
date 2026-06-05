package com.linrun.trigger.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollection;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollectionFactory;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.common.AcademicCommonToolRuntimeFactory;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;
import com.linrun.trigger.http.McpAdminHandler;
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
public class AcademicToolCallbackFactory {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AcademicCodeInterpreterPort> codeInterpreterPort;
    private final ObjectProvider<AcademicWebFetchPort> webFetchPort;
    private final ObjectProvider<AcademicDataAnalysisPort> dataAnalysisPort;
    private final ObjectProvider<AcademicReportPort> reportPort;
    private final ObjectProvider<AcademicImageGenerationPort> imageGenerationPort;
    private final ObjectProvider<AcademicMultimodalAnalysisPort> multimodalAnalysisPort;
    private final ObjectProvider<AcademicDeepSearchPort> deepSearchPort;
    private final ObjectProvider<AcademicFileToolPort> fileToolPort;
    private final ObjectProvider<AcademicScriptRunnerPort> scriptRunnerPort;
    private final ObjectProvider<AcademicTableRagPort> tableRagPort;
    private final ObjectProvider<AcademicNl2SqlPort> nl2SqlPort;
    private final ObjectProvider<AcademicTradeAuditPort> tradeAuditPort;
    private final ObjectProvider<McpAdminHandler> mcpAdminHandler;

    public AcademicToolCallbackFactory(ObjectMapper objectMapper,
                                       ObjectProvider<AcademicCodeInterpreterPort> codeInterpreterPort,
                                       ObjectProvider<AcademicWebFetchPort> webFetchPort,
                                       ObjectProvider<AcademicDataAnalysisPort> dataAnalysisPort,
                                       ObjectProvider<AcademicReportPort> reportPort,
                                       ObjectProvider<AcademicImageGenerationPort> imageGenerationPort,
                                       ObjectProvider<AcademicMultimodalAnalysisPort> multimodalAnalysisPort,
                                       ObjectProvider<AcademicDeepSearchPort> deepSearchPort,
                                       ObjectProvider<AcademicFileToolPort> fileToolPort,
                                       ObjectProvider<AcademicScriptRunnerPort> scriptRunnerPort,
                                       ObjectProvider<AcademicTableRagPort> tableRagPort,
                                       ObjectProvider<AcademicNl2SqlPort> nl2SqlPort,
                                       ObjectProvider<AcademicTradeAuditPort> tradeAuditPort,
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
        this.tradeAuditPort = tradeAuditPort;
        this.mcpAdminHandler = mcpAdminHandler;
    }

    public ToolCallback[] create(String scene,
                                 String userId,
                                 String sessionId,
                                 boolean webAccessEnabled) {
        AcademicToolCollection collection = createCollection(scene, webAccessEnabled);
        return createCallbacks(objectMapper, collection, userId, sessionId);
    }

    public List<Map<String, Object>> preview(String scene, boolean webAccessEnabled) {
        return createCollection(scene, webAccessEnabled).listDefinitions().stream()
                .map(AcademicToolCallbackFactory::previewDefinition)
                .toList();
    }

    private AcademicToolCollection createCollection(String scene, boolean webAccessEnabled) {
        AcademicToolRuntimeRegistry registry = AcademicCommonToolRuntimeFactory.builder()
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
                .tradeAuditPort(available(tradeAuditPort))
                .build()
                .buildRegistry();
        registerMcpTools(registry);
        return new AcademicToolCollectionFactory(registry)
                .buildByCategories(scene, categories(webAccessEnabled));
    }

    private void registerMcpTools(AcademicToolRuntimeRegistry registry) {
        McpAdminHandler handler = available(mcpAdminHandler);
        if (handler == null) {
            return;
        }
        for (Map<String, Object> definition : handler.listAgentToolDefinitions()) {
            AcademicToolDefinition toolDefinition = AcademicToolDefinition.fromMcpDefinition(
                    definition,
                    String.valueOf(definition.getOrDefault("category", "mcp")),
                    String.valueOf(definition.getOrDefault("source", "mcp")));
            registry.register(toolDefinition, command -> handler.callAgentTool(
                    command.getToolName(),
                    command.getArguments()));
        }
    }

    public static ToolCallback[] createCallbacks(ObjectMapper objectMapper,
                                                 AcademicToolCollection collection,
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
                                               AcademicToolCollection collection,
                                               AcademicToolDefinition definition,
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
                                   AcademicToolCollection collection,
                                   AcademicToolDefinition definition,
                                   Map<String, Object> arguments,
                                   String userId,
                                   String sessionId) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        AcademicToolCallCommand command = AcademicToolCallCommand.builder(definition.getName())
                .action("spring-ai/tool-call")
                .requestId(firstText(safeArguments.get("requestId"), "tool-" + UUID.randomUUID()))
                .sessionId(sessionId)
                .userId(userId)
                .arguments(safeArguments)
                .build();
        try {
            AcademicToolCallResult result = collection.call(command);
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

    private static String description(AcademicToolDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getDescription());
        builder.append("\n\n类别：").append(definition.getCategory());
        builder.append("；来源：").append(definition.getSource()).append("。");
        builder.append("\n返回值是 JSON，包含 success、result、artifactIds 和错误信息。");
        return builder.toString();
    }

    private static Map<String, Object> previewDefinition(AcademicToolDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", definition.getName());
        result.put("description", definition.getDescription());
        result.put("category", definition.getCategory());
        result.put("source", definition.getSource());
        result.put("requiredArguments", definition.getRequiredArguments());
        return result;
    }

    private static List<String> categories(boolean webAccessEnabled) {
        if (webAccessEnabled) {
            return List.of("analysis", "report", "planning", "code", "image",
                    "multimodal", "file", "skill", "data", "trade", "mcp", "web", "search");
        }
        return List.of("analysis", "report", "planning", "code", "image",
                "multimodal", "file", "skill", "data", "trade", "mcp");
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
