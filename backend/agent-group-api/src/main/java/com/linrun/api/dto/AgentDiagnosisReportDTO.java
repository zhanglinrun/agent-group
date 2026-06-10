package com.linrun.api.dto;

import java.util.List;

/**
 * Agent 诊断报告 DTO
 */
public class AgentDiagnosisReportDTO {
    
    private String runId;
    private String sessionId;
    private String level;
    private List<DiagnosisItemDTO> issues;
    private String summary;
    private Long elapsedMs;
    private Integer toolCallCount;
    private Integer failedToolCount;
    private Double quotaConsumed;
    private Integer replanCount;
    private Integer llmCallCount;
    private Integer artifactCount;
    private Double toolSuccessRate;

    public static class DiagnosisItemDTO {
        private String level;
        private String code;
        private String message;

        public DiagnosisItemDTO() {
        }

        public DiagnosisItemDTO(String level, String code, String message) {
            this.level = level;
            this.code = code;
            this.message = message;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public AgentDiagnosisReportDTO() {
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<DiagnosisItemDTO> getIssues() {
        return issues;
    }

    public void setIssues(List<DiagnosisItemDTO> issues) {
        this.issues = issues;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Integer getFailedToolCount() {
        return failedToolCount;
    }

    public Integer getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(Integer toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public void setFailedToolCount(Integer failedToolCount) {
        this.failedToolCount = failedToolCount;
    }

    public Double getQuotaConsumed() {
        return quotaConsumed;
    }

    public void setQuotaConsumed(Double quotaConsumed) {
        this.quotaConsumed = quotaConsumed;
    }

    public Integer getReplanCount() {
        return replanCount;
    }

    public void setReplanCount(Integer replanCount) {
        this.replanCount = replanCount;
    }

    public Integer getLlmCallCount() {
        return llmCallCount;
    }

    public void setLlmCallCount(Integer llmCallCount) {
        this.llmCallCount = llmCallCount;
    }

    public Integer getArtifactCount() {
        return artifactCount;
    }

    public void setArtifactCount(Integer artifactCount) {
        this.artifactCount = artifactCount;
    }

    public Double getToolSuccessRate() {
        return toolSuccessRate;
    }

    public void setToolSuccessRate(Double toolSuccessRate) {
        this.toolSuccessRate = toolSuccessRate;
    }
}
