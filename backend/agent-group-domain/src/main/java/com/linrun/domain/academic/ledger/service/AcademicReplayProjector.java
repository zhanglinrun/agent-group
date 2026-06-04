package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AcademicReplayProjector {

    public AcademicReplayResponse project(AcademicAgentRun run,
                                          List<AcademicLlmInvocation> llmInvocations,
                                          List<AcademicToolInvocation> toolInvocations,
                                          List<AcademicArtifact> artifacts) {
        AcademicReplayResponse response = new AcademicReplayResponse();
        if (run == null) {
            return response;
        }
        response.setSessionId(safe(run.getSessionId()));
        response.setRunId(safe(run.getRunId()));
        response.setStatus(safe(run.getStatus()));

        AtomicInteger sequence = new AtomicInteger(1);
        List<GuideStreamEvent<Map<String, Object>>> events = new ArrayList<>();
        events.add(event("run_start", run, sequence, runStart(run)));
        events.add(event("plan_delta", run, sequence, plan(run, hasSearchTool(toolInvocations))));
        for (AcademicToolInvocation invocation : safeList(toolInvocations)) {
            events.add(event("tool_call", run, sequence, toolCall(invocation)));
            events.add(event("tool_result", run, sequence, toolResult(invocation)));
        }
        for (AcademicLlmInvocation invocation : safeList(llmInvocations)) {
            events.add(event("llm_delta", run, sequence, llm(invocation)));
        }
        for (AcademicArtifact artifact : safeList(artifacts)) {
            events.add(event("artifact_delta", run, sequence, artifact(artifact)));
        }
        events.add(event(AcademicAgentRun.STATUS_FAILED.equals(run.getStatus()) ? "run_error" : "run_done",
                run, sequence, runDone(run)));
        response.setEvents(events);
        return response;
    }

    private GuideStreamEvent<Map<String, Object>> event(String name,
                                                        AcademicAgentRun run,
                                                        AtomicInteger sequence,
                                                        Map<String, Object> data) {
        GuideStreamEvent<Map<String, Object>> event = GuideStreamEvent.of(
                name, safe(run.getSessionId()), safe(run.getRequestId()), sequence.getAndIncrement(), data);
        if (run.getStartedAt() != null) {
            event.setTimestamp(run.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return event;
    }

    private Map<String, Object> runStart(AcademicAgentRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", safe(run.getRunId()));
        data.put("taskType", safe(run.getTaskType()));
        data.put("question", safe(run.getQuestion()));
        data.put("model", safe(run.getModelName()));
        data.put("status", safe(run.getStatus()));
        data.put("startedAt", run.getStartedAt());
        return data;
    }

    private Map<String, Object> plan(AcademicAgentRun run, boolean webSearchUsed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", safe(run.getRunId()));
        data.put("steps", planSteps(run.getTaskType(), webSearchUsed));
        return data;
    }

    private List<String> planSteps(String taskType, boolean webSearchUsed) {
        return switch (safe(taskType)) {
            case "file" -> List.of("读取文件", "检索相关内容", "生成回答");
            case "ppt" -> List.of("拆解主题", webSearchUsed ? "搜索资料" : "整理素材", "生成演示文稿");
            case "deep" -> List.of("拆解问题", webSearchUsed ? "搜索资料" : "梳理已有信息", "汇总结论");
            case "skills", "manual-skills" -> List.of("选择技能", "执行工具", "整理产物");
            default -> List.of("理解问题", webSearchUsed ? "检索或搜索" : "组织回答", "生成回答");
        };
    }

    private boolean hasSearchTool(List<AcademicToolInvocation> toolInvocations) {
        for (AcademicToolInvocation invocation : safeList(toolInvocations)) {
            String toolName = safe(invocation.getToolName()).toLowerCase();
            if (toolName.contains("search") || toolName.contains("tavily") || toolName.contains("搜索")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toolCall(AcademicToolInvocation invocation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", safe(invocation.getInvocationId()));
        data.put("toolCallId", safe(invocation.getToolCallId()));
        data.put("toolName", safe(invocation.getToolName()));
        data.put("action", safe(invocation.getAction()));
        data.put("argumentsJson", safe(invocation.getArgumentsJson()));
        data.put("status", safe(invocation.getStatus()));
        data.put("startedAt", invocation.getStartedAt());
        return data;
    }

    private Map<String, Object> toolResult(AcademicToolInvocation invocation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", safe(invocation.getInvocationId()));
        data.put("toolName", safe(invocation.getToolName()));
        data.put("status", safe(invocation.getStatus()));
        data.put("resultSummary", safe(invocation.getResultSummary()));
        data.put("resultJson", safe(invocation.getResultJson()));
        data.put("retryCount", invocation.getRetryCount() == null ? 0 : invocation.getRetryCount());
        data.put("latencyMillis", invocation.getLatencyMillis() == null ? 0L : invocation.getLatencyMillis());
        data.put("errorMessage", safe(invocation.getErrorMessage()));
        return data;
    }

    private Map<String, Object> llm(AcademicLlmInvocation invocation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", safe(invocation.getInvocationId()));
        data.put("modelName", safe(invocation.getModelName()));
        data.put("status", safe(invocation.getStatus()));
        data.put("promptSummary", safe(invocation.getPromptSummary()));
        data.put("promptTokens", invocation.getPromptTokens() == null ? 0L : invocation.getPromptTokens());
        data.put("completionTokens", invocation.getCompletionTokens() == null ? 0L : invocation.getCompletionTokens());
        data.put("totalTokens", invocation.getTotalTokens() == null ? 0L : invocation.getTotalTokens());
        data.put("latencyMillis", invocation.getLatencyMillis() == null ? 0L : invocation.getLatencyMillis());
        data.put("fallbackUsed", Boolean.TRUE.equals(invocation.getFallbackUsed()));
        data.put("errorMessage", safe(invocation.getErrorMessage()));
        return data;
    }

    private Map<String, Object> artifact(AcademicArtifact artifact) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactId", safe(artifact.getArtifactId()));
        data.put("artifactType", safe(artifact.getArtifactType()));
        data.put("title", safe(artifact.getTitle()));
        data.put("content", safe(artifact.getContent()));
        data.put("downloadUrl", safe(artifact.getDownloadUrl()));
        data.put("runId", safe(artifact.getRunId()));
        data.put("toolInvocationId", safe(artifact.getToolInvocationId()));
        data.put("sourceType", safe(artifact.getSourceType()));
        data.put("sourceName", safe(artifact.getSourceName()));
        return data;
    }

    private Map<String, Object> runDone(AcademicAgentRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", safe(run.getRunId()));
        data.put("status", safe(run.getStatus()));
        data.put("summary", safe(run.getFinalSummary()));
        data.put("errorCode", safe(run.getErrorCode()));
        data.put("errorMessage", safe(run.getErrorMessage()));
        data.put("durationMillis", run.getDurationMillis() == null ? 0L : run.getDurationMillis());
        data.put("finishedAt", run.getFinishedAt());
        return data;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
