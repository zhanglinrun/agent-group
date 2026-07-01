package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentWorkspaceImageGenerateResponse implements Serializable {

    private String requestId;
    private String sessionId;
    private String runId;
    private String invocationId;
    private String toolName;
    private String title;
    private String summary;
    private String provider;
    private Boolean usedFallback;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private List<FileRef> fileRefs = new ArrayList<>();
    private List<ArtifactRef> artifactRefs = new ArrayList<>();

    @Data
    public static class FileRef implements Serializable {
        private String artifactId;
        private String fileName;
        private String downloadUrl;
        private String previewUrl;
        private String contentType;
        private Long fileSize;
    }

    @Data
    public static class ArtifactRef implements Serializable {
        private String artifactId;
        private String sessionId;
        private String runId;
        private String toolInvocationId;
        private String artifactType;
        private String title;
        private String fileName;
        private String downloadUrl;
        private String previewUrl;
    }
}















