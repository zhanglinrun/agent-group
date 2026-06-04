package com.linrun.trigger.http;

import com.linrun.types.exception.AppException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class McpToolRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    public McpToolRegistry register(Map<String, Object> definition,
                                    Function<Map<String, Object>, Map<String, Object>> handler) {
        String name = definition == null ? "" : String.valueOf(definition.getOrDefault("name", ""));
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name cannot be blank");
        }
        tools.put(name, new RegisteredTool(definition, handler));
        return this;
    }

    public List<Map<String, Object>> listTools() {
        return tools.values().stream()
                .map(RegisteredTool::definition)
                .map(LinkedHashMap::new)
                .map(map -> (Map<String, Object>) map)
                .toList();
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new AppException("MCP_0001", "unknown tool: " + name);
        }
        return tool.handler().apply(arguments == null ? Map.of() : arguments);
    }

    public List<String> toolNames() {
        return new ArrayList<>(tools.keySet());
    }

    private record RegisteredTool(Map<String, Object> definition,
                                  Function<Map<String, Object>, Map<String, Object>> handler) {
    }
}
