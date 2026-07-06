package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentRunDetailResponse implements Serializable {

    private Run run;
    private Evidence evidence;
    private AgentDiagnosisReportDTO diagnosis;
    private List<LlmInvocation> llmInvocations = new ArrayList<>();
    private List<ToolInvocation> toolInvocations = new ArrayList<>();
    private List<AgentSessionDetailResponse.Artifact> artifacts = new ArrayList<>();

    @Data
    public static class Evidence implements Serializable {
        private Mode mode;
        private PlanEvidence plan;
        private List<ToolFailure> failedTools = new ArrayList<>();
        private List<String> replanReasons = new ArrayList<>();
        private Integer toolCallCount;
        private Integer failedToolCount;
        private Integer replanCount;
        private Integer llmCallCount;
        private Integer artifactCount;
        private Double quotaConsumed;
        private Double toolSuccessRate;
        private String diagnosisLevel;
        private String diagnosisSummary;
    }

    @Data
    public static class Mode implements Serializable {
        private String taskType;
        private String executionMode;
        private String modeFamily;
        private String agentType;
        private String reason;
    }

    @Data
    public static class PlanEvidence implements Serializable {
        private String title;
        private Integer revisionCount;
        private List<PlanStep> steps = new ArrayList<>();
    }

    @Data
    public static class PlanStep implements Serializable {
        private String stepId;
        private String instruction;
        private Integer order;
        private String status;
        private String assignedAgent;
        private List<String> dependencies = new ArrayList<>();
    }

    @Data
    public static class ToolFailure implements Serializable {
        private String invocationId;
        private String toolName;
        private String errorMessage;
        private Boolean recoveredByLaterTool;
        private String replanReason;
    }

    @Data
    public static class Run implements Serializable {
        private String runId;
        private String sessionId;
        private String projectId;
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
        private Map<String, Object> structuredOutput;
        private List<AgentSessionDetailResponse.Artifact> artifactRefs = new ArrayList<>();
        private Integer artifactCount;
        private String status;
        private Integer retryCount;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long latencyMillis;
    }
}














