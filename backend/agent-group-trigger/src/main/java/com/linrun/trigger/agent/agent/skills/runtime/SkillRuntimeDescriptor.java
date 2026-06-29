package com.linrun.trigger.agent.agent.skills.runtime;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SkillRuntimeDescriptor(
        String name,
        String description,
        List<String> modes,
        List<String> taskTypes,
        List<String> inputParameters,
        String outputConstraints,
        List<String> permissions,
        String version,
        boolean enabled,
        List<String> boundTools,
        List<String> resources
) {

    public SkillRuntimeDescriptor {
        name = safe(name);
        description = safe(description);
        modes = copy(modes);
        taskTypes = copy(taskTypes);
        inputParameters = copy(inputParameters);
        outputConstraints = safe(outputConstraints);
        permissions = copy(permissions);
        version = StringUtils.hasText(version) ? version.trim() : "manual";
        boundTools = copy(boundTools);
        resources = copy(resources);
    }

    public boolean matches(String mode, String taskType) {
        return enabled
                && matchesScope(modes, mode)
                && matchesScope(taskTypes, taskType);
    }

    public String toWorkerSummary() {
        return toWorkerSummary((java.util.Set<String>) null);
    }

    public String toWorkerSummary(java.util.Set<String> registeredTools) {
        StringBuilder builder = new StringBuilder("- ")
                .append(name)
                .append(": ")
                .append(description);
        if (!boundTools.isEmpty()) {
            List<String> usableTools = registeredTools == null
                    ? boundTools
                    : boundTools.stream().filter(registeredTools::contains).toList();
            if (!usableTools.isEmpty()) {
                builder.append("\n  tools: ").append(String.join(", ", usableTools));
            }
            List<String> missingTools = registeredTools == null
                    ? List.of()
                    : boundTools.stream().filter(tool -> !registeredTools.contains(tool)).toList();
            if (!missingTools.isEmpty()) {
                builder.append("\n  unavailableTools: ").append(String.join(", ", missingTools));
            }
        }
        if (!inputParameters.isEmpty()) {
            builder.append("\n  inputs: ").append(String.join(", ", inputParameters));
        }
        if (!outputConstraints.isBlank()) {
            builder.append("\n  output: ").append(outputConstraints);
        }
        if (!resources.isEmpty()) {
            builder.append("\n  resources: ").append(String.join(", ", resources));
        }
        return builder.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("description", description);
        data.put("modes", modes);
        data.put("taskTypes", taskTypes);
        data.put("inputParameters", inputParameters);
        data.put("outputConstraints", outputConstraints);
        data.put("permissions", permissions);
        data.put("version", version);
        data.put("enabled", enabled);
        data.put("boundTools", boundTools);
        data.put("resources", resources);
        return data;
    }

    private static boolean matchesScope(List<String> configured, String value) {
        if (configured == null || configured.isEmpty()) {
            return true;
        }
        String normalized = normalize(value);
        return configured.stream()
                .map(SkillRuntimeDescriptor::normalize)
                .anyMatch(scope -> "*".equals(scope) || scope.equals(normalized));
    }

    private static List<String> copy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase().replace('_', '-');
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
