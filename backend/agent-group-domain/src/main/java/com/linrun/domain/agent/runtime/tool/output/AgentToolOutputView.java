package com.linrun.domain.agent.runtime.tool.output;

import com.linrun.domain.agent.model.AgentArtifact;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentToolOutputView {

    private final String toolName;
    private final String requestId;
    private final String sessionId;
    private final String toolCallId;
    private final String status;
    private final String errorMessage;
    private final LocalDateTime createdAt;
    private final Map<String, Object> structuredOutput;
    private final List<AgentToolFileRef> fileRefs;
    private final List<AgentArtifact> artifactRefs;

    private AgentToolOutputView(Builder builder) {
        this.toolName = safe(builder.toolName);
        this.requestId = safe(builder.requestId);
        this.sessionId = safe(builder.sessionId);
        this.toolCallId = safe(builder.toolCallId);
        this.status = safe(builder.status);
        this.errorMessage = safe(builder.errorMessage);
        this.createdAt = builder.createdAt;
        this.structuredOutput = builder.structuredOutput == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(builder.structuredOutput);
        this.fileRefs = builder.fileRefs == null ? List.of() : List.copyOf(builder.fileRefs);
        this.artifactRefs = builder.artifactRefs == null ? List.of() : List.copyOf(builder.artifactRefs);
    }

    public static Builder builder(String toolName) {
        return new Builder(toolName);
    }

    public String getToolName() {
        return toolName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getStructuredOutput() {
        return new LinkedHashMap<>(structuredOutput);
    }

    public List<AgentToolFileRef> getFileRefs() {
        return fileRefs;
    }

    public List<AgentArtifact> getArtifactRefs() {
        return artifactRefs;
    }

    public int getArtifactCount() {
        return artifactRefs.size();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String toolName;
        private String requestId = "";
        private String sessionId = "";
        private String toolCallId = "";
        private String status = "";
        private String errorMessage = "";
        private LocalDateTime createdAt;
        private Map<String, Object> structuredOutput = new LinkedHashMap<>();
        private List<AgentToolFileRef> fileRefs = List.of();
        private List<AgentArtifact> artifactRefs = List.of();

        private Builder(String toolName) {
            this.toolName = toolName;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder structuredOutput(Map<String, Object> structuredOutput) {
            this.structuredOutput = structuredOutput == null ? new LinkedHashMap<>() : new LinkedHashMap<>(structuredOutput);
            return this;
        }

        public Builder fileRefs(List<AgentToolFileRef> fileRefs) {
            this.fileRefs = fileRefs == null ? List.of() : List.copyOf(fileRefs);
            return this;
        }

        public Builder artifactRefs(List<AgentArtifact> artifactRefs) {
            this.artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
            return this;
        }

        public AgentToolOutputView build() {
            return new AgentToolOutputView(this);
        }
    }
}















