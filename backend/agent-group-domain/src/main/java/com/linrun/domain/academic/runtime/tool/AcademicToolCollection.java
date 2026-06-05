package com.linrun.domain.academic.runtime.tool;

import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AcademicToolCollection {

    private final String scene;
    private final AcademicToolRuntimeRegistry runtimeRegistry;
    private final Set<String> enabledToolNames;
    private String currentTask = "";
    private Map<String, String> toolRoles = new LinkedHashMap<>();

    AcademicToolCollection(String scene,
                           AcademicToolRuntimeRegistry runtimeRegistry,
                           Collection<String> enabledToolNames) {
        if (runtimeRegistry == null) {
            throw new IllegalArgumentException("tool runtime registry cannot be null");
        }
        this.scene = StringUtils.hasText(scene) ? scene.trim() : "default";
        this.runtimeRegistry = runtimeRegistry;
        this.enabledToolNames = sanitize(enabledToolNames);
    }

    public String getScene() {
        return scene;
    }

    public List<AcademicToolDefinition> listDefinitions() {
        return runtimeRegistry.listEnabledDefinitions().stream()
                .filter(definition -> enabledToolNames.isEmpty() || enabledToolNames.contains(definition.getName()))
                .toList();
    }

    public List<String> toolNames() {
        return listDefinitions().stream()
                .map(AcademicToolDefinition::getName)
                .toList();
    }

    public boolean contains(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        return toolNames().contains(toolName.trim());
    }

    public AcademicToolCallResult call(AcademicToolCallCommand command) {
        String toolName = command == null ? "" : command.getToolName();
        if (!contains(toolName)) {
            throw new AppException("TOOL_0101", "tool is not enabled in scene " + scene + ": " + toolName);
        }
        return runtimeRegistry.call(command);
    }

    public AcademicToolCollection select(String nextScene, Collection<String> nextToolNames) {
        AcademicToolCollection selected = new AcademicToolCollection(nextScene, runtimeRegistry, nextToolNames);
        selected.restoreState(snapshotState());
        return selected;
    }

    public void updateCurrentTask(String currentTask) {
        this.currentTask = currentTask == null ? "" : currentTask.trim();
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public void updateToolRoles(Map<String, String> toolRoles) {
        this.toolRoles = new LinkedHashMap<>();
        if (toolRoles == null) {
            return;
        }
        toolRoles.forEach((toolName, role) -> {
            if (StringUtils.hasText(toolName) && StringUtils.hasText(role)) {
                this.toolRoles.put(toolName.trim(), role.trim());
            }
        });
    }

    public String getToolRole(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "";
        }
        return toolRoles.getOrDefault(toolName.trim(), "");
    }

    public TaskScopedStateSnapshot snapshotState() {
        return new TaskScopedStateSnapshot(currentTask, new LinkedHashMap<>(toolRoles));
    }

    public void restoreState(TaskScopedStateSnapshot snapshot) {
        if (snapshot == null) {
            currentTask = "";
            toolRoles = new LinkedHashMap<>();
            return;
        }
        currentTask = snapshot.currentTask() == null ? "" : snapshot.currentTask().trim();
        toolRoles = snapshot.toolRoles() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.toolRoles());
    }

    private Set<String> sanitize(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            if (StringUtils.hasText(toolName)) {
                result.add(toolName.trim());
            }
        }
        return result;
    }

    public record TaskScopedStateSnapshot(String currentTask, Map<String, String> toolRoles) {
    }
}
