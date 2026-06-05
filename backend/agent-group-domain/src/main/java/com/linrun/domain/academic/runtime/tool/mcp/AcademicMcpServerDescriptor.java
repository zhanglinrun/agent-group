package com.linrun.domain.academic.runtime.tool.mcp;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class AcademicMcpServerDescriptor {

    private final String serverId;
    private final String name;
    private final String endpoint;
    private final String transport;
    private final boolean enabled;
    private final Map<String, Object> metadata;

    private AcademicMcpServerDescriptor(Builder builder) {
        this.serverId = safe(builder.serverId);
        this.name = StringUtils.hasText(builder.name) ? builder.name.trim() : this.serverId;
        this.endpoint = safe(builder.endpoint);
        this.transport = StringUtils.hasText(builder.transport) ? builder.transport.trim() : "streamable_http";
        this.enabled = builder.enabled;
        this.metadata = builder.metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(builder.metadata);
        if (!StringUtils.hasText(serverId)) {
            throw new IllegalArgumentException("mcp server id cannot be blank");
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("mcp endpoint cannot be blank");
        }
    }

    public static Builder builder(String serverId) {
        return new Builder(serverId);
    }

    public AcademicMcpServerDescriptor withEnabled(boolean enabled) {
        return builder(serverId)
                .name(name)
                .endpoint(endpoint)
                .transport(transport)
                .enabled(enabled)
                .metadata(metadata)
                .build();
    }

    public String getServerId() {
        return serverId;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getTransport() {
        return transport;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String serverId;
        private String name = "";
        private String endpoint = "";
        private String transport = "streamable_http";
        private boolean enabled = true;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        private Builder(String serverId) {
            this.serverId = serverId;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                this.metadata.put(key.trim(), value);
            }
            return this;
        }

        public AcademicMcpServerDescriptor build() {
            return new AcademicMcpServerDescriptor(this);
        }
    }
}
