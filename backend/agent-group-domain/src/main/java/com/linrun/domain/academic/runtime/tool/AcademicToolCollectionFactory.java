package com.linrun.domain.academic.runtime.tool;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AcademicToolCollectionFactory {

    private final AcademicToolRuntimeRegistry runtimeRegistry;

    public AcademicToolCollectionFactory(AcademicToolRuntimeRegistry runtimeRegistry) {
        if (runtimeRegistry == null) {
            throw new IllegalArgumentException("tool runtime registry cannot be null");
        }
        this.runtimeRegistry = runtimeRegistry;
    }

    public AcademicToolCollection buildAll(String scene) {
        return new AcademicToolCollection(scene, runtimeRegistry, List.of());
    }

    public AcademicToolCollection buildByToolNames(String scene, Collection<String> toolNames) {
        return new AcademicToolCollection(scene, runtimeRegistry, toolNames);
    }

    public AcademicToolCollection buildByCategories(String scene, Collection<String> categories) {
        Set<String> categorySet = sanitize(categories);
        if (categorySet.isEmpty()) {
            return buildAll(scene);
        }
        List<String> toolNames = runtimeRegistry.listEnabledDefinitions().stream()
                .filter(definition -> categorySet.contains(definition.getCategory()))
                .map(AcademicToolDefinition::getName)
                .toList();
        return buildByToolNames(scene, toolNames);
    }

    public AcademicToolCollection buildBySources(String scene, Collection<String> sources) {
        Set<String> sourceSet = sanitize(sources);
        if (sourceSet.isEmpty()) {
            return buildAll(scene);
        }
        List<String> toolNames = runtimeRegistry.listEnabledDefinitions().stream()
                .filter(definition -> sourceSet.contains(definition.getSource()))
                .map(AcademicToolDefinition::getName)
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















