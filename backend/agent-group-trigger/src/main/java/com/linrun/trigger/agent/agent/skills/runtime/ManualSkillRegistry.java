package com.linrun.trigger.agent.agent.skills.runtime;

import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillScriptDefinition;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ManualSkillRegistry implements SkillRegistry {

    private final SkillManager skillManager;

    public ManualSkillRegistry(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public List<SkillRuntimeDescriptor> availableSkills(String mode, String taskType) {
        if (skillManager == null) {
            return List.of();
        }
        return skillManager.getSkills().stream()
                .map(this::descriptor)
                .filter(skill -> skill.matches(mode, taskType))
                .toList();
    }

    private SkillRuntimeDescriptor descriptor(SkillMetadata metadata) {
        Map<String, Object> frontmatter = frontmatter(metadata);
        List<String> resources = new ArrayList<>();
        if (metadata.skillFile() != null) {
            resources.add(metadata.skillFile().toString());
        }
        metadata.scripts().values().stream()
                .map(SkillScriptDefinition::relativePath)
                .filter(StringUtils::hasText)
                .map(path -> "scripts/" + path)
                .forEach(resources::add);
        List<String> tools = metadata.allowedTools() == null ? List.of() : metadata.allowedTools();
        return new SkillRuntimeDescriptor(
                metadata.name(),
                firstText(frontmatter, metadata.description(), "description", "desc"),
                list(frontmatter, "modes", "mode", "agentTypes", "agentType"),
                list(frontmatter, "taskTypes", "taskType", "tasks", "scenes", "scene"),
                list(frontmatter, "inputParameters", "inputs", "parameters"),
                firstText(frontmatter, "", "outputConstraints", "output", "outputConstraint"),
                list(frontmatter, "permissions", "permission"),
                firstText(frontmatter, "manual", "version"),
                bool(frontmatter.get("enabled"), true),
                tools,
                resources
        );
    }

    private Map<String, Object> frontmatter(SkillMetadata metadata) {
        if (metadata == null || metadata.skillFile() == null || !Files.isRegularFile(metadata.skillFile())) {
            return Map.of();
        }
        try {
            String content = Files.readString(metadata.skillFile());
            if (!content.startsWith("---")) {
                return Map.of();
            }
            int end = content.indexOf("---", 3);
            if (end < 0) {
                return Map.of();
            }
            Object loaded = new Yaml().load(content.substring(3, end));
            if (!(loaded instanceof Map<?, ?> raw)) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> list(Map<String, Object> data, String... keys) {
        Object value = firstValue(data, keys);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                    .map(item -> String.valueOf(item).trim())
                    .toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String firstText(Map<String, Object> data, String fallback, String... keys) {
        Object value = firstValue(data, keys);
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Object firstValue(Map<String, Object> data, String... keys) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }
}
