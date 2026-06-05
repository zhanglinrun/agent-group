package com.linrun.domain.academic.runtime.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class AcademicToolCallCommand {

    private final String toolName;
    private final String action;
    private final String requestId;
    private final String sessionId;
    private final String userId;
    private final String runId;
    private final Map<String, Object> arguments;

    private AcademicToolCallCommand(Builder builder) {
        this.toolName = safe(builder.toolName);
        this.action = safe(builder.action);
        this.requestId = safe(builder.requestId);
        this.sessionId = safe(builder.sessionId);
        this.userId = safe(builder.userId);
        this.runId = safe(builder.runId);
        this.arguments = builder.arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(builder.arguments);
    }

    public static Builder builder(String toolName) {
        return new Builder(toolName);
    }

    public String getToolName() {
        return toolName;
    }

    public String getAction() {
        return action;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRunId() {
        return runId;
    }

    public Map<String, Object> getArguments() {
        return new LinkedHashMap<>(arguments);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String toolName;
        private String action = "tools/call";
        private String requestId = "";
        private String sessionId = "";
        private String userId = "";
        private String runId = "";
        private Map<String, Object> arguments = Map.of();

        private Builder(String toolName) {
            this.toolName = toolName;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public AcademicToolCallCommand build() {
            return new AcademicToolCallCommand(this);
        }
    }
}
