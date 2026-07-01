package com.linrun.domain.agent.runtime.tool.mcp;

import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.AgentToolSchemaNormalizer;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentMcpToolDescriptor {

    private final String serverId;
    private final String toolName;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final boolean enabled;
    private final LocalDateTime discoveredAt;

    private AgentMcpToolDescriptor(Builder builder) {
        this.serverId = safe(builder.serverId);
        this.toolName = safe(builder.toolName);
        this.description = safe(builder.description);
        this.inputSchema = AgentToolSchemaNormalizer.normalize(builder.inputSchema);
        this.enabled = builder.enabled;
        this.discoveredAt = builder.discoveredAt == null ? LocalDateTime.now() : builder.discoveredAt;
        if (!StringUtils.hasText(serverId)) {
            throw new IllegalArgumentException("mcp server id cannot be blank");
        }
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("mcp tool name cannot be blank");
        }
    }

    public static Builder builder(String serverId, String toolName) {
        return new Builder(serverId, toolName);
    }

    public String qualifiedName() {
        return serverId + "." + toolName;
    }

    public AgentToolDefinition toToolDefinition() {
        return AgentToolDefinition.builder(qualifiedName())
                .description(description)
                .category("mcp")
                .source(serverId)
                .inputSchema(inputSchema)
                .enabled(enabled)
                .build();
    }

    public String getServerId() {
        return serverId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInputSchema() {
        return new LinkedHashMap<>(inputSchema);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    private static Map<String, Object> emptySchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String serverId;
        private final String toolName;
        private String description = "";
        private Map<String, Object> inputSchema = emptySchema();
        private boolean enabled = true;
        private LocalDateTime discoveredAt;

        private Builder(String serverId, String toolName) {
            this.serverId = serverId;
            this.toolName = toolName;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder discoveredAt(LocalDateTime discoveredAt) {
            this.discoveredAt = discoveredAt;
            return this;
        }

        public AgentMcpToolDescriptor build() {
            return new AgentMcpToolDescriptor(this);
        }
    }
}















