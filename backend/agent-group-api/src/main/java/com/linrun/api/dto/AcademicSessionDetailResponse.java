package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AcademicSessionDetailResponse implements Serializable {

    private String sessionId;
    private List<Message> messages = new ArrayList<>();
    private List<AcademicReplayResponse> replays = new ArrayList<>();

    @Data
    public static class Message implements Serializable {
        private String role;
        private String content;
        private String imageUrl;
        private java.time.LocalDateTime createTime;
        private List<Artifact> artifacts = new ArrayList<>();
        private List<Reference> references = new ArrayList<>();
        private Object recommend;
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
