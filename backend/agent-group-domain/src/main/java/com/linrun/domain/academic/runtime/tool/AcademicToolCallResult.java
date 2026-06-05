package com.linrun.domain.academic.runtime.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicToolCallResult {

    private final String toolName;
    private final String action;
    private final boolean success;
    private final Map<String, Object> result;
    private final String errorCode;
    private final String errorMessage;
    private final long latencyMillis;
    private final List<String> artifactIds;

    private AcademicToolCallResult(Builder builder) {
        this.toolName = safe(builder.toolName);
        this.action = safe(builder.action);
        this.success = builder.success;
        this.result = builder.result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(builder.result);
        this.errorCode = safe(builder.errorCode);
        this.errorMessage = safe(builder.errorMessage);
        this.latencyMillis = Math.max(0L, builder.latencyMillis);
        this.artifactIds = builder.artifactIds == null ? List.of() : List.copyOf(builder.artifactIds);
    }

    public static Builder success(String toolName, String action, Map<String, Object> result, long latencyMillis) {
        return new Builder(toolName, true)
                .action(action)
                .result(result)
                .latencyMillis(latencyMillis);
    }

    public static Builder failure(String toolName, String action, String errorCode, String errorMessage, long latencyMillis) {
        return new Builder(toolName, false)
                .action(action)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .latencyMillis(latencyMillis);
    }

    public String getToolName() {
        return toolName;
    }

    public String getAction() {
        return action;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getResult() {
        return new LinkedHashMap<>(result);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public List<String> getArtifactIds() {
        return artifactIds;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {

        private final String toolName;
        private final boolean success;
        private String action = "tools/call";
        private Map<String, Object> result = Map.of();
        private String errorCode = "";
        private String errorMessage = "";
        private long latencyMillis;
        private List<String> artifactIds = List.of();

        private Builder(String toolName, boolean success) {
            this.toolName = toolName;
            this.success = success;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder result(Map<String, Object> result) {
            this.result = result;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder latencyMillis(long latencyMillis) {
            this.latencyMillis = latencyMillis;
            return this;
        }

        public Builder artifactIds(List<String> artifactIds) {
            this.artifactIds = artifactIds;
            return this;
        }

        public AcademicToolCallResult build() {
            return new AcademicToolCallResult(this);
        }
    }
}
