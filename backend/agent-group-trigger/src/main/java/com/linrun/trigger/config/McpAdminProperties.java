package com.linrun.trigger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "agent.group.mcp", ignoreInvalidFields = true)
public class McpAdminProperties {

    private String adminStateFile = "";
    private boolean persistImportedState = true;
    private List<Server> servers = new ArrayList<>();

    public String getAdminStateFile() {
        return adminStateFile;
    }

    public void setAdminStateFile(String adminStateFile) {
        this.adminStateFile = adminStateFile;
    }

    public boolean isPersistImportedState() {
        return persistImportedState;
    }

    public void setPersistImportedState(boolean persistImportedState) {
        this.persistImportedState = persistImportedState;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers == null ? new ArrayList<>() : servers;
    }

    public static class Server {

        private String serverId = "";
        private String name = "";
        private String endpoint = "";
        private String transport = "streamable_http";
        private boolean enabled = true;
        private boolean discoverOnStartup = false;
        private boolean cacheDiscoveredTools = true;
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private Map<String, Object> discoveryRequest = new LinkedHashMap<>();
        private List<Tool> tools = new ArrayList<>();

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDiscoverOnStartup() {
            return discoverOnStartup;
        }

        public void setDiscoverOnStartup(boolean discoverOnStartup) {
            this.discoverOnStartup = discoverOnStartup;
        }

        public boolean isCacheDiscoveredTools() {
            return cacheDiscoveredTools;
        }

        public void setCacheDiscoveredTools(boolean cacheDiscoveredTools) {
            this.cacheDiscoveredTools = cacheDiscoveredTools;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
        }

        public Map<String, Object> getDiscoveryRequest() {
            return discoveryRequest;
        }

        public void setDiscoveryRequest(Map<String, Object> discoveryRequest) {
            this.discoveryRequest = discoveryRequest == null ? new LinkedHashMap<>() : discoveryRequest;
        }

        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools = tools == null ? new ArrayList<>() : tools;
        }
    }

    public static class Tool {

        private String toolName = "";
        private String name = "";
        private String description = "";
        private Map<String, Object> inputSchema = new LinkedHashMap<>();
        private boolean enabled = true;

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema == null ? new LinkedHashMap<>() : inputSchema;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}















