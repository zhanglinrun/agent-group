package com.linrun.trigger.agent.agent.skills.manual.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SkillScriptDefinition(
        String scriptName,
        String relativePath,
        Path absolutePath,
        String runtime,
        String description,
        Map<String, Object> metadata
) {

    public SkillScriptDefinition {
        Objects.requireNonNull(scriptName, "scriptName must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        Objects.requireNonNull(runtime, "runtime must not be null");
        metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String toSummaryLine() {
        String desc = description == null || description.isBlank() ? "未提供说明" : description;
        return "- " + scriptName + " | runtime=" + runtime + " | path=" + relativePath + " | " + desc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String scriptName;
        private String relativePath;
        private Path absolutePath;
        private String runtime;
        private String description;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder scriptName(String scriptName) {
            this.scriptName = scriptName;
            return this;
        }

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }

        public Builder absolutePath(Path absolutePath) {
            this.absolutePath = absolutePath;
            return this;
        }

        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            return this;
        }

        public SkillScriptDefinition build() {
            return new SkillScriptDefinition(scriptName, relativePath, absolutePath, runtime, description, metadata);
        }
    }
}
