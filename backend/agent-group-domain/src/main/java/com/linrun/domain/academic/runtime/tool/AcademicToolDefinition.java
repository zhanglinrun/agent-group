package com.linrun.domain.academic.runtime.tool;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicToolDefinition {

    private final String name;
    private final String description;
    private final String category;
    private final String source;
    private final Map<String, Object> inputSchema;
    private final List<String> requiredArguments;
    private final boolean enabled;

    private AcademicToolDefinition(Builder builder) {
        this.name = safe(builder.name);
        this.description = safe(builder.description);
        this.category = StringUtils.hasText(builder.category) ? builder.category.trim() : "general";
        this.source = StringUtils.hasText(builder.source) ? builder.source.trim() : "local";
        this.inputSchema = AcademicToolSchemaNormalizer.normalize(builder.inputSchema);
        this.requiredArguments = List.copyOf(requiredArguments(builder.requiredArguments, this.inputSchema));
        this.enabled = builder.enabled;
        if (!StringUtils.hasText(this.name)) {
            throw new IllegalArgumentException("tool name cannot be blank");
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @SuppressWarnings("unchecked")
    public static AcademicToolDefinition fromMcpDefinition(Map<String, Object> definition,
                                                           String category,
                                                           String source) {
        Map<String, Object> safeDefinition = definition == null ? Map.of() : definition;
        Object schema = safeDefinition.get("inputSchema");
        Map<String, Object> inputSchema = schema instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of("type", "object", "properties", Map.of(), "required", List.of());
        return builder(String.valueOf(safeDefinition.getOrDefault("name", "")))
                .description(String.valueOf(safeDefinition.getOrDefault("description", "")))
                .category(category)
                .source(source)
                .inputSchema(inputSchema)
                .enabled(true)
                .build();
    }

    public Map<String, Object> toMcpDefinition() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("description", description);
        map.put("inputSchema", copyMap(inputSchema));
        return map;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getSource() {
        return source;
    }

    public Map<String, Object> getInputSchema() {
        return copyMap(inputSchema);
    }

    public List<String> getRequiredArguments() {
        return requiredArguments;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static List<String> requiredArguments(List<String> configuredRequired,
                                                  Map<String, Object> inputSchema) {
        if (configuredRequired != null && !configuredRequired.isEmpty()) {
            return configuredRequired.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return AcademicToolSchemaNormalizer.requiredArguments(inputSchema);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String name;
        private String description = "";
        private String category = "general";
        private String source = "local";
        private Map<String, Object> inputSchema = Map.of("type", "object", "properties", Map.of(), "required", List.of());
        private List<String> requiredArguments = List.of();
        private boolean enabled = true;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder requiredArguments(List<String> requiredArguments) {
            this.requiredArguments = requiredArguments == null ? List.of() : requiredArguments;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AcademicToolDefinition build() {
            return new AcademicToolDefinition(this);
        }
    }
}
