package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentWorkspaceImageHistoryResponse implements Serializable {

    private String sessionId;
    private Integer total = 0;
    private List<AgentWorkspaceImageGenerateResponse.ArtifactRef> items = new ArrayList<>();
    private Integer batchTotal = 0;
    private List<Batch> batches = new ArrayList<>();

    @Data
    public static class Batch implements Serializable {
        private String requestId;
        private String sessionId;
        private String runId;
        private String prompt;
        private String summary;
        private String status;
        private String mode;
        private String model;
        private String quality;
        private String aspectRatio;
        private String size;
        private Integer batchCount = 0;
        private Integer sourceImageCount = 0;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMillis;
        private List<AgentWorkspaceImageGenerateResponse.ArtifactRef> images = new ArrayList<>();
    }
}















