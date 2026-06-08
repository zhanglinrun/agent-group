package com.linrun.trigger.http.agent;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollection;
import com.linrun.domain.academic.runtime.tool.AcademicToolCollectionFactory;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.types.exception.AppException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class McpToolRegistry {

    private final AcademicToolRuntimeRegistry runtimeRegistry = new AcademicToolRuntimeRegistry();
    private final AcademicToolCollectionFactory collectionFactory = new AcademicToolCollectionFactory(runtimeRegistry);

    public McpToolRegistry register(Map<String, Object> definition,
                                    Function<Map<String, Object>, Map<String, Object>> handler) {
        AcademicToolDefinition toolDefinition =
                AcademicToolDefinition.fromMcpDefinition(definition, "mcp", "agent-group");
        runtimeRegistry.register(toolDefinition, command -> handler.apply(command.getArguments()));
        return this;
    }

    public List<Map<String, Object>> listTools() {
        return runtimeRegistry.listEnabledDefinitions().stream()
                .map(AcademicToolDefinition::toMcpDefinition)
                .toList();
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        AcademicToolCallResult result = runtimeRegistry.call(AcademicToolCallCommand.builder(name)
                .action("tools/call")
                .arguments(arguments == null ? Map.of() : arguments)
                .build());
        if (!result.isSuccess()) {
            throw new AppException(result.getErrorCode(), result.getErrorMessage());
        }
        return result.getResult();
    }

    public List<String> toolNames() {
        return new ArrayList<>(runtimeRegistry.toolNames());
    }

    public AcademicToolCollection asToolCollection(String scene) {
        return collectionFactory.buildAll(scene);
    }

    public AcademicToolCollection asToolCollection(String scene, List<String> toolNames) {
        return collectionFactory.buildByToolNames(scene, toolNames);
    }
}
