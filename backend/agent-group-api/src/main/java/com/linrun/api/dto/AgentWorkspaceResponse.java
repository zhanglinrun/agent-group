package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentWorkspaceResponse implements Serializable {

    private String projectId;
    private String title;
    private String researchQuestion;
    private String targetVenue;
    private String writingStatus;
    private String progressNote;
    private Integer fileCount;
    private Integer pendingPatchCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ProjectFile> files = new ArrayList<>();
    private List<ProjectPatch> patches = new ArrayList<>();

    @Data
    public static class ProjectFile implements Serializable {
        private String fileId;
        private String fileName;
        private String fileType;
        private String folderType;
        private String summary;
        private String contentPreview;
        private LocalDateTime createTime;
    }

    @Data
    public static class ProjectPatch implements Serializable {
        private String patchId;
        private String fileId;
        private String title;
        private String reason;
        private String beforeText;
        private String afterText;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime applyTime;
    }
}















