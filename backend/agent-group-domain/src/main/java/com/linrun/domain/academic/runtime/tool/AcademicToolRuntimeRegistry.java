package com.linrun.domain.academic.runtime.tool;

import com.linrun.types.exception.AppException;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputProjector;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AcademicToolRuntimeRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private List<AcademicToolDefinition> definitionCache = List.of();
    private boolean dirty = true;

    public synchronized AcademicToolRuntimeRegistry register(AcademicToolDefinition definition,
                                                            Function<AcademicToolCallCommand, Map<String, Object>> handler) {
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

    public synchronized AcademicToolRuntimeRegistry registerStructured(
            AcademicToolDefinition definition,
            Function<AcademicToolCallCommand, AcademicToolStructuredOutput> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("structured tool handler cannot be null");
        }
        return register(definition, command -> AcademicToolOutputProjector.toResultMap(handler.apply(command)));
    }

    public synchronized List<AcademicToolDefinition> listDefinitions() {
        if (dirty) {
            definitionCache = tools.values().stream()
                    .map(RegisteredTool::definition)
                    .toList();
            dirty = false;
        }
        return new ArrayList<>(definitionCache);
    }

    public synchronized List<AcademicToolDefinition> listEnabledDefinitions() {
        return listDefinitions().stream()
                .filter(AcademicToolDefinition::isEnabled)
                .toList();
    }

    public synchronized AcademicToolRuntimeSummary runtimeSummary() {
        return runtimeSummary(List.of());
    }

    public synchronized AcademicToolRuntimeSummary runtimeSummary(Collection<String> expectedToolNames) {
        List<AcademicToolDefinition> definitions = listDefinitions();
        List<String> registeredToolNames = definitions.stream()
                .map(AcademicToolDefinition::getName)
                .toList();
        List<String> enabledToolNames = definitions.stream()
                .filter(AcademicToolDefinition::isEnabled)
                .map(AcademicToolDefinition::getName)
                .toList();
        List<String> disabledToolNames = definitions.stream()
                .filter(definition -> !definition.isEnabled())
                .map(AcademicToolDefinition::getName)
                .toList();
        Set<String> enabledToolNameSet = new LinkedHashSet<>(enabledToolNames);
        List<String> missingExpectedToolNames = sanitizeExpectedToolNames(expectedToolNames).stream()
                .filter(toolName -> !enabledToolNameSet.contains(toolName))
                .toList();
        return new AcademicToolRuntimeSummary(
                definitions.size(),
                enabledToolNames.size(),
                disabledToolNames.size(),
                registeredToolNames,
                enabledToolNames,
                disabledToolNames,
                missingExpectedToolNames,
                countBy(definitions, AcademicToolDefinition::getCategory),
                countBy(definitions, AcademicToolDefinition::getSource));
    }

    public AcademicToolCallResult call(AcademicToolCallCommand command) {
        long startedAt = System.nanoTime();
        RegisteredTool tool = resolve(command == null ? "" : command.getToolName());
        validate(tool.definition(), command);
        try {
            Map<String, Object> result = tool.handler().apply(command);
            Map<String, Object> safeResult = result == null ? Map.of() : result;
            return AcademicToolCallResult.success(
                    tool.definition().getName(),
                    command.getAction(),
                    safeResult,
                    elapsedMillis(startedAt))
                    .artifactIds(AcademicToolOutputProjector.extractArtifactIds(safeResult))
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            return AcademicToolCallResult.failure(
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

    private Map<String, Integer> countBy(List<AcademicToolDefinition> definitions,
                                         Function<AcademicToolDefinition, String> classifier) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AcademicToolDefinition definition : definitions) {
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

    private void validate(AcademicToolDefinition definition, AcademicToolCallCommand command) {
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

    private record RegisteredTool(AcademicToolDefinition definition,
                                  Function<AcademicToolCallCommand, Map<String, Object>> handler) {
    }
}
