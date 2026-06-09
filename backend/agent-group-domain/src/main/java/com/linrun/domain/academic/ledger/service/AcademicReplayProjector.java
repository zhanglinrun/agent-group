package com.linrun.domain.academic.ledger.service;

import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.QuotaStreamEvent;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowProjector;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowProgress;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowProgressProjector;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowProgressResult;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowStage;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentRunPlanFactory;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputReader;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputView;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AcademicReplayProjector {

    private final AcademicToolOutputReader toolOutputReader = new AcademicToolOutputReader();
    private final AcademicAgentRunPlanFactory runPlanFactory = new AcademicAgentRunPlanFactory();
    private final AcademicAgentFlowProjector flowProjector = new AcademicAgentFlowProjector();
    private final AcademicAgentFlowProgressProjector flowProgressProjector = new AcademicAgentFlowProgressProjector();

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
        List<QuotaStreamEvent<Map<String, Object>>> events = new ArrayList<>();
        List<AcademicToolInvocation> tools = safeList(toolInvocations);
        boolean webSearchUsed = hasSearchTool(tools);
        AcademicAgentPlan executionPlan = runPlanFactory.build(run.getTaskType(), webSearchUsed);
        int planRevision = 1;
        events.add(event("run_start", run, sequence, runStart(run)));
        events.add(event("plan_delta", run, sequence, plan(run, executionPlan, planRevision, "")));
        AcademicAgentFlowProgressResult startProgress = flowProgressProjector.start(executionPlan);
        int currentStageIndex = startProgress.getCurrentStageIndex();
        events.addAll(flowProgressEvents(run, sequence, startProgress));
        for (int index = 0; index < tools.size(); index++) {
            AcademicToolInvocation invocation = tools.get(index);
            AcademicAgentFlowProgressResult toolProgress = flowProgressProjector.advanceToTool(
                    executionPlan, currentStageIndex, invocation.getToolName());
            currentStageIndex = toolProgress.getCurrentStageIndex();
            events.addAll(flowProgressEvents(run, sequence, toolProgress));
            events.add(event("tool_call", run, sequence, toolCall(invocation)));
            events.add(event("tool_result", run, sequence, toolResult(invocation, artifacts)));
            if (isFailed(invocation) && hasLaterSuccessTool(tools, index)) {
                String replanReason = replanReason(invocation);
                AcademicAgentFlowProgressResult replanProgress = flowProgressProjector.markReplanned(
                        executionPlan, currentStageIndex, replanReason);
                events.addAll(flowProgressEvents(run, sequence, replanProgress));
                executionPlan = runPlanFactory.build(run.getTaskType(), webSearchUsed);
                currentStageIndex = -1;
                planRevision++;
                events.add(event("plan_delta", run, sequence,
                        plan(run, executionPlan, planRevision, replanReason)));
                AcademicAgentFlowProgressResult replannedStart = flowProgressProjector.start(executionPlan);
                currentStageIndex = replannedStart.getCurrentStageIndex();
                events.addAll(flowProgressEvents(run, sequence, replannedStart));
            }
        }
        for (AcademicLlmInvocation invocation : safeList(llmInvocations)) {
            events.add(event("llm_delta", run, sequence, llm(invocation)));
        }
        for (AcademicArtifact artifact : safeList(artifacts)) {
            events.add(event("artifact_delta", run, sequence, artifact(artifact)));
        }
        AcademicAgentFlowProgressResult finalProgress = AcademicAgentRun.STATUS_FAILED.equals(run.getStatus())
                ? flowProgressProjector.blockCurrent(executionPlan, currentStageIndex, run.getErrorMessage())
                : flowProgressProjector.completeRemaining(executionPlan, currentStageIndex);
        events.addAll(flowProgressEvents(run, sequence, finalProgress));
        events.add(event(AcademicAgentRun.STATUS_FAILED.equals(run.getStatus()) ? "run_error" : "run_done",
                run, sequence, runDone(run)));
        response.setEvents(events);
        return response;
    }

    private QuotaStreamEvent<Map<String, Object>> event(String name,
                                                        AcademicAgentRun run,
                                                        AtomicInteger sequence,
                                                        Map<String, Object> data) {
        QuotaStreamEvent<Map<String, Object>> event = QuotaStreamEvent.of(
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

    private Map<String, Object> plan(AcademicAgentRun run,
                                     AcademicAgentPlan executionPlan,
                                     int revision,
                                     String replanReason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", safe(run.getRunId()));
        data.put("revision", Math.max(1, revision));
        data.put("changeType", revision > 1 ? "replan" : "initial");
        data.put("replanReason", safe(replanReason));
        data.put("title", revision > 1 ? executionPlan.getTitle() + "（重规划 " + revision + "） : executionPlan.getTitle());
        data.put("steps", executionPlan.getSteps().stream()
                .map(AcademicPlanStep::getInstruction)
                .toList());
        data.put("structuredSteps", executionPlan.getSteps().stream()
                .map(this::planStep)
                .toList());
        data.put("flowStages", flowProjector.buildRemainingStages(executionPlan).stream()
                .map(this::flowStage)
                .toList());
        return data;
    }

    private Map<String, Object> planStep(AcademicPlanStep step) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stepId", step.getStepId());
        data.put("instruction", step.getInstruction());
        data.put("order", step.getOrder());
        data.put("status", step.getStatus());
        data.put("assignedAgent", step.getAssignedAgent());
        data.put("dependencies", step.getDependencies());
        return data;
    }

    private Map<String, Object> flowStage(AcademicAgentFlowStage stage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stageIndex", stage.getStageIndex());
        data.put("stepIds", stage.stepIds());
        data.put("steps", stage.getSteps().stream()
                .map(this::planStep)
                .toList());
        return data;
    }

    private List<QuotaStreamEvent<Map<String, Object>>> flowProgressEvents(AcademicAgentRun run,
                                                                           AtomicInteger sequence,
                                                                           AcademicAgentFlowProgressResult progress) {
        if (progress == null || progress.getEvents().isEmpty()) {
            return List.of();
        }
        List<QuotaStreamEvent<Map<String, Object>>> events = new ArrayList<>();
        for (AcademicAgentFlowProgress item : progress.getEvents()) {
            events.add(event("flow_delta", run, sequence, flowProgress(run, item)));
        }
        return events;
    }

    private Map<String, Object> flowProgress(AcademicAgentRun run, AcademicAgentFlowProgress progress) {
        Map<String, Object> data = flowStage(progress.getStage());
        data.put("runId", safe(run.getRunId()));
        data.put("status", progress.getStatus());
        data.put("message", progress.getMessage());
        return data;
    }

    private List<String> planSteps(String taskType, boolean webSearchUsed) {
        return switch (safe(taskType)) {
            case "file" -> List.of("读取文件", "检索相关内容, "生成回答");
            case "ppt" -> List.of("拆解主题", webSearchUsed ? "搜索资料" : "整理素材", "生成演示文稿");
            case "deep" -> List.of("拆解问题", webSearchUsed ? "搜索资料" : "梳理已有信息", "汇总结论);
            case "image" -> List.of("拆解画面", "调用图像工具", "整理图像产物");
            case "data" -> List.of("确认口径", "查询或分析数据, "输出结论");
            case "skills", "manual-skills" -> List.of("选择技能, "执行工具", "整理产物");
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

    private boolean hasLaterSuccessTool(List<AcademicToolInvocation> toolInvocations, int currentIndex) {
        for (int index = currentIndex + 1; index < toolInvocations.size(); index++) {
            AcademicToolInvocation invocation = toolInvocations.get(index);
            if (AcademicAgentRun.STATUS_SUCCESS.equals(safe(invocation.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isFailed(AcademicToolInvocation invocation) {
        return invocation != null && AcademicAgentRun.STATUS_FAILED.equals(safe(invocation.getStatus()));
    }

    private String replanReason(AcademicToolInvocation invocation) {
        String toolName = safe(invocation.getToolName());
        String reason = safe(invocation.getErrorMessage());
        if (reason.isEmpty()) {
            reason = safe(invocation.getResultSummary());
        }
        if (reason.isEmpty()) {
            reason = "工具执行失败后切换后继续步验;
        }
        return toolName.isEmpty() ? "计划已重规划） + reason : "计划已重规划） + toolName + " 失败） + reason;
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

    private Map<String, Object> toolResult(AcademicToolInvocation invocation,
                                           List<AcademicArtifact> artifacts) {
        AcademicToolOutputView outputView = toolOutputReader.read(invocation, artifacts);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invocationId", safe(invocation.getInvocationId()));
        data.put("toolCallId", safe(invocation.getToolCallId()));
        data.put("toolName", safe(invocation.getToolName()));
        data.put("status", safe(invocation.getStatus()));
        data.put("resultSummary", safe(invocation.getResultSummary()));
        data.put("resultJson", safe(invocation.getResultJson()));
        data.put("structuredOutput", outputView.getStructuredOutput());
        data.put("resultKind", toolResultKind(invocation.getToolName(), outputView));
        data.put("artifactCount", outputView.getArtifactCount());
        data.put("fileRefs", outputView.getFileRefs().stream()
                .map(AcademicToolFileRef::toMap)
                .toList());
        data.put("artifactRefs", outputView.getArtifactRefs().stream()
                .map(this::artifact)
                .toList());
        data.put("retryCount", invocation.getRetryCount() == null ? 0 : invocation.getRetryCount());
        data.put("latencyMillis", invocation.getLatencyMillis() == null ? 0L : invocation.getLatencyMillis());
        data.put("errorMessage", safe(invocation.getErrorMessage()));
        return data;
    }

    private String toolResultKind(String toolName, AcademicToolOutputView outputView) {
        String normalized = safe(toolName).toLowerCase();
        if (normalized.contains(AcademicToolOutputNames.CODE_INTERPRETER)
                || normalized.contains(AcademicToolOutputNames.SCRIPT_RUNNER)) {
            return "code";
        }
        if (normalized.contains(AcademicToolOutputNames.IMAGE_GENERATION)) {
            return "image";
        }
        if (normalized.contains(AcademicToolOutputNames.MULTIMODAL_AGENT)) {
            return "multimodal";
        }
        if (normalized.contains(AcademicToolOutputNames.DEEP_SEARCH) || normalized.contains("search")) {
            return "search";
        }
        if (normalized.contains(AcademicToolOutputNames.WEB_FETCH) || normalized.contains("web")) {
            return "web";
        }
        if (normalized.contains(AcademicToolOutputNames.NL2SQL)) {
            return "sql";
        }
        if (normalized.contains(AcademicToolOutputNames.TABLE_RAG)) {
            return "schema";
        }
        if (normalized.contains(AcademicToolOutputNames.DATA_ANALYSIS)) {
            return "data";
        }
        if (normalized.contains(AcademicToolOutputNames.FILE_TOOL)
                || (normalized.contains(AcademicToolOutputNames.REPORT_TOOL)
                && outputView != null && !outputView.getFileRefs().isEmpty())) {
            return "file";
        }
        return "summary";
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















