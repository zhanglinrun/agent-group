package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicRunDetailResponse implements Serializable {

    private Run run;
    private List<LlmInvocation> llmInvocations = new ArrayList<>();
    private List<ToolInvocation> toolInvocations = new ArrayList<>();
    private List<AcademicSessionDetailResponse.Artifact> artifacts = new ArrayList<>();

    @Data
    public static class Run implements Serializable {
        private String runId;
        private String sessionId;
        private String requestId;
        private String taskType;
        private String question;
        private String status;
        private String modelName;
        private String finalSummary;
        private String errorCode;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMillis;
    }

    @Data
    public static class LlmInvocation implements Serializable {
        private String invocationId;
        private String modelName;
        private String promptSummary;
        private String responseText;
        private String status;
        private Long promptTokens;
        private Long completionTokens;
        private Long totalTokens;
        private Boolean fallbackUsed;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long latencyMillis;
    }

    @Data
    public static class ToolInvocation implements Serializable {
        private String invocationId;
        private String toolCallId;
        private String toolName;
        private String action;
        private String argumentsJson;
        private String resultSummary;
        private String resultJson;
        private String status;
        private Integer retryCount;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long latencyMillis;
    }
}
