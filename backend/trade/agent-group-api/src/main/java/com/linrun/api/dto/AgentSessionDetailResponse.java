package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentSessionDetailResponse implements Serializable {

    private String sessionId;
    private List<Message> messages = new ArrayList<>();
    private List<AgentReplayResponse> replays = new ArrayList<>();
    private MemorySnapshot memory = new MemorySnapshot();

    @Data
    public static class Message implements Serializable {
        private String messageId;
        private String role;
        private String content;
        private String imageUrl;
        private java.time.LocalDateTime createTime;
        private List<Artifact> artifacts = new ArrayList<>();
        private List<Reference> references = new ArrayList<>();
        private Object recommend;
    }

    @Data
    public static class MemorySnapshot implements Serializable {
        private String sessionId;
        private String summary;
        private String historyDialogue;
        private List<RunMemory> runs = new ArrayList<>();
        private List<ToolObservation> toolObservations = new ArrayList<>();
        private List<Artifact> reusableArtifacts = new ArrayList<>();
    }

    @Data
    public static class RunMemory implements Serializable {
        private String runId;
        private String requestId;
        private String taskType;
        private String question;
        private String status;
        private String finalSummary;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    @Data
    public static class ToolObservation implements Serializable {
        private String runId;
        private String invocationId;
        private String toolCallId;
        private String toolName;
        private String action;
        private String argumentsJson;
        private String resultSummary;
        private String status;
        private String errorMessage;
        private LocalDateTime createdAt;
        private List<Artifact> artifactRefs = new ArrayList<>();
    }

    @Data
    public static class Artifact implements Serializable {
        private String artifactId;
        private String artifactType;
        private String title;
        private String fileName;
        private String downloadUrl;
        private Long fileSize;
        private String runId;
        private String toolInvocationId;
        private String sourceType;
        private String sourceName;
    }

    @Data
    public static class Reference implements Serializable {
        private String title;
        private String url;
        private String content;
    }
}















