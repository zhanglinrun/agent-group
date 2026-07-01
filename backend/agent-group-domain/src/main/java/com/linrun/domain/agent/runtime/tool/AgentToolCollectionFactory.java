package com.linrun.domain.agent.runtime.tool;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AgentToolCollectionFactory {

    private final AgentToolRuntimeRegistry runtimeRegistry;

    public AgentToolCollectionFactory(AgentToolRuntimeRegistry runtimeRegistry) {
        if (runtimeRegistry == null) {
            throw new IllegalArgumentException("tool runtime registry cannot be null");
        }
        this.runtimeRegistry = runtimeRegistry;
    }

    public AgentToolCollection buildAll(String scene) {
        return new AgentToolCollection(scene, runtimeRegistry, List.of());
    }

    public AgentToolCollection buildByToolNames(String scene, Collection<String> toolNames) {
        return new AgentToolCollection(scene, runtimeRegistry, toolNames);
    }

    public AgentToolCollection buildByCategories(String scene, Collection<String> categories) {
        Set<String> categorySet = sanitize(categories);
        if (categorySet.isEmpty()) {
            return buildAll(scene);
        }
        List<String> toolNames = runtimeRegistry.listEnabledDefinitions().stream()
                .filter(definition -> categorySet.contains(definition.getCategory()))
                .map(AgentToolDefinition::getName)
                .toList();
        return buildByToolNames(scene, toolNames);
    }

    public AgentToolCollection buildBySources(String scene, Collection<String> sources) {
        Set<String> sourceSet = sanitize(sources);
        if (sourceSet.isEmpty()) {
            return buildAll(scene);
        }
        List<String> toolNames = runtimeRegistry.listEnabledDefinitions().stream()
                .filter(definition -> sourceSet.contains(definition.getSource()))
                .map(AgentToolDefinition::getName)
                .toList();
        return buildByToolNames(scene, toolNames);
    }

    private Set<String> sanitize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }
}















