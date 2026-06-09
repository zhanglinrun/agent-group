package com.linrun.trigger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "agent.group.agent-admin", ignoreInvalidFields = true)
public class AgentAdminConfigProperties {

    private String stateFile = "";
    private boolean persistImportedState = true;
    private List<Config> configs = new ArrayList<>();

    public String getStateFile() {
        return stateFile;
    }

    public void setStateFile(String stateFile) {
        this.stateFile = stateFile;
    }

    public boolean isPersistImportedState() {
        return persistImportedState;
    }

    public void setPersistImportedState(boolean persistImportedState) {
        this.persistImportedState = persistImportedState;
    }

    public List<Config> getConfigs() {
        return configs;
    }

    public void setConfigs(List<Config> configs) {
        this.configs = configs == null ? new ArrayList<>() : configs;
    }

    public static class Config {

        private String configId = "";
        private String category = "";
        private String name = "";
        private String description = "";
        private String content = "";
        private boolean enabled = true;
        private int orderNo = 0;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getConfigId() {
            return configId;
        }

        public void setConfigId(String configId) {
            this.configId = configId;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
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

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(int orderNo) {
            this.orderNo = orderNo;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
        }
    }
}















