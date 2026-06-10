package com.linrun.domain.academic.runtime.diagnosis;

import java.time.LocalDateTime;

/**
 * Agent 执行指标
 * 用于记录和查询 Agent 执行的关键数据
 */
public class AgentExecutionMetrics {
    
    private String sessionId;
    private String runId;
    private String executionMode;
    private Long elapsedMs;
    private Integer toolCallCount;
    private Integer failedToolCount;
    private Double quotaConsumed;
    private Integer replanCount;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String exceptionMessage;

    public AgentExecutionMetrics() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentExecutionMetrics metrics = new AgentExecutionMetrics();

        public Builder sessionId(String sessionId) {
            metrics.sessionId = sessionId;
            return this;
        }

        public Builder runId(String runId) {
            metrics.runId = runId;
            return this;
        }

        public Builder executionMode(String executionMode) {
            metrics.executionMode = executionMode;
            return this;
        }

        public Builder elapsedMs(Long elapsedMs) {
            metrics.elapsedMs = elapsedMs;
            return this;
        }

        public Builder toolCallCount(Integer toolCallCount) {
            metrics.toolCallCount = toolCallCount;
            return this;
        }

        public Builder failedToolCount(Integer failedToolCount) {
            metrics.failedToolCount = failedToolCount;
            return this;
        }

        public Builder quotaConsumed(Double quotaConsumed) {
            metrics.quotaConsumed = quotaConsumed;
            return this;
        }

        public Builder replanCount(Integer replanCount) {
            metrics.replanCount = replanCount;
            return this;
        }

        public Builder status(String status) {
            metrics.status = status;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            metrics.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            metrics.endTime = endTime;
            return this;
        }

        public Builder exceptionMessage(String exceptionMessage) {
            metrics.exceptionMessage = exceptionMessage;
            return this;
        }

        public AgentExecutionMetrics build() {
            return metrics;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Integer getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(Integer toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public Integer getFailedToolCount() {
        return failedToolCount;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }
}
