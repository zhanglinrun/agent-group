package com.linrun.domain.agent.runtime.tool;

import com.linrun.types.exception.AppException;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputProjector;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AgentToolRuntimeRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private List<AgentToolDefinition> definitionCache = List.of();
    private boolean dirty = true;

    public synchronized AgentToolRuntimeRegistry register(AgentToolDefinition definition,
                                                            Function<AgentToolCallCommand, Map<String, Object>> handler) {
        if (definition == null) {
            throw new IllegalArgumentException("tool definition cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("tool handler cannot be null");
        }
        tools.put(definition.getName(), new RegisteredTool(definition, handler));
        dirty = true;
        return this;
    }

    public synchronized AgentToolRuntimeRegistry registerStructured(
            AgentToolDefinition definition,
            Function<AgentToolCallCommand, AgentToolStructuredOutput> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("structured tool handler cannot be null");
        }
        return register(definition, command -> AgentToolOutputProjector.toResultMap(handler.apply(command)));
    }

    public synchronized List<AgentToolDefinition> listDefinitions() {
        if (dirty) {
            definitionCache = tools.values().stream()
                    .map(RegisteredTool::definition)
                    .toList();
            dirty = false;
        }
        return new ArrayList<>(definitionCache);
    }

    public synchronized List<AgentToolDefinition> listEnabledDefinitions() {
        return listDefinitions().stream()
                .filter(AgentToolDefinition::isEnabled)
                .toList();
    }

    public synchronized AgentToolRuntimeSummary runtimeSummary() {
        return runtimeSummary(List.of());
    }

    public synchronized AgentToolRuntimeSummary runtimeSummary(Collection<String> expectedToolNames) {
        List<AgentToolDefinition> definitions = listDefinitions();
        List<String> registeredToolNames = definitions.stream()
                .map(AgentToolDefinition::getName)
                .toList();
        List<String> enabledToolNames = definitions.stream()
                .filter(AgentToolDefinition::isEnabled)
                .map(AgentToolDefinition::getName)
                .toList();
        List<String> disabledToolNames = definitions.stream()
                .filter(definition -> !definition.isEnabled())
                .map(AgentToolDefinition::getName)
                .toList();
        Set<String> enabledToolNameSet = new LinkedHashSet<>(enabledToolNames);
        List<String> missingExpectedToolNames = sanitizeExpectedToolNames(expectedToolNames).stream()
                .filter(toolName -> !enabledToolNameSet.contains(toolName))
                .toList();
        return new AgentToolRuntimeSummary(
                definitions.size(),
                enabledToolNames.size(),
                disabledToolNames.size(),
                registeredToolNames,
                enabledToolNames,
                disabledToolNames,
                missingExpectedToolNames,
                countBy(definitions, AgentToolDefinition::getCategory),
                countBy(definitions, AgentToolDefinition::getSource));
    }

    public AgentToolCallResult call(AgentToolCallCommand command) {
        long startedAt = System.nanoTime();
        RegisteredTool tool = resolve(command == null ? "" : command.getToolName());
        validate(tool.definition(), command);
        try {
            Map<String, Object> result = tool.handler().apply(command);
            Map<String, Object> safeResult = result == null ? Map.of() : result;
            return AgentToolCallResult.success(
                    tool.definition().getName(),
                    command.getAction(),
                    safeResult,
                    elapsedMillis(startedAt))
                    .artifactIds(AgentToolOutputProjector.extractArtifactIds(safeResult))
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            return AgentToolCallResult.failure(
                    tool.definition().getName(),
                    command.getAction(),
                    "TOOL_EXECUTE_FAILED",
                    e.getMessage(),
                    elapsedMillis(startedAt)).build();
        }
    }

    public synchronized List<String> toolNames() {
        return new ArrayList<>(tools.keySet());
    }

    private List<String> sanitizeExpectedToolNames(Collection<String> expectedToolNames) {
        if (expectedToolNames == null || expectedToolNames.isEmpty()) {
            return List.of();
        }
        return expectedToolNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, Integer> countBy(List<AgentToolDefinition> definitions,
                                         Function<AgentToolDefinition, String> classifier) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AgentToolDefinition definition : definitions) {
            String key = classifier.apply(definition);
            String safeKey = StringUtils.hasText(key) ? key.trim() : "unknown";
            counts.put(safeKey, counts.getOrDefault(safeKey, 0) + 1);
        }
        return counts;
    }

    private synchronized RegisteredTool resolve(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            throw new AppException("TOOL_0001", "tool name cannot be blank");
        }
        RegisteredTool tool = tools.get(toolName);
        if (tool == null) {
            throw new AppException("TOOL_0002", "unknown tool: " + toolName);
        }
        if (!tool.definition().isEnabled()) {
            throw new AppException("TOOL_0003", "tool disabled: " + toolName);
        }
        return tool;
    }

    private void validate(AgentToolDefinition definition, AgentToolCallCommand command) {
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        for (String required : definition.getRequiredArguments()) {
            Object value = arguments.get(required);
            if (value == null || (value instanceof String text && !StringUtils.hasText(text))) {
                throw new AppException("TOOL_0004", "missing required tool argument: " + required);
            }
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record RegisteredTool(AgentToolDefinition definition,
                                  Function<AgentToolCallCommand, Map<String, Object>> handler) {
    }
}















