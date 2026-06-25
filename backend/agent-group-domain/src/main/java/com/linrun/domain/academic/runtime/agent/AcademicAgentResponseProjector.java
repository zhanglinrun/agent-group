package com.linrun.domain.academic.runtime.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.runtime.diagnosis.AgentDiagnosisService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 响应投影器
 *
 * 负责将领域对象投影为流式响应格式
 * 职责：数据转换、格式化、JSON 组装
 */
@Component
public class AcademicAgentResponseProjector {

    /**
     * 投影运行开始事件
     */
    public String projectRunStart(AcademicAgentRun run, String projectId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRunId());
        data.put("sessionId", run.getSessionId());
        data.put("projectId", projectId);
        data.put("status", "running");
        return AgentResponse.metadata(toJson(data));
    }

    /**
     * 投影计划
     */
    public String projectPlan(AcademicAgentPlan plan) {
        if (plan == null) {
            return AgentResponse.plan("", "[]");
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (AcademicPlanStep step : plan.getSteps()) {
            Map<String, Object> stepData = new LinkedHashMap<>();
            stepData.put("stepId", step.getStepId());
            stepData.put("instruction", step.getInstruction());
            stepData.put("order", step.getOrder());
            stepData.put("status", step.getStatus());
            stepData.put("assignedAgent", step.getAssignedAgent());
            stepData.put("dependencies", step.getDependencies());
            steps.add(stepData);
        }

        return AgentResponse.plan(plan.getTitle(), toJson(steps));
    }

    /**
     * 投影重规划
     */
    public String projectReplan(AcademicAgentPlan oldPlan, AcademicAgentPlan newPlan, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", nullToBlank(reason));
        data.put("oldPlan", oldPlan == null ? List.of() : projectPlanSteps(oldPlan));
        data.put("newPlan", newPlan == null ? List.of() : projectPlanSteps(newPlan));
        return AgentResponse.replan(nullToBlank(reason), toJson(data));
    }

    /**
     * 投影执行进度
     */
    public String projectProgress(AcademicAgentFlowProgress progress) {
        if (progress == null) {
            return AgentResponse.text("");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", progress.getStage());
        data.put("stageIndex", progress.getStageIndex());
        data.put("status", progress.getStatus());
        data.put("message", progress.getMessage());

        return AgentResponse.metadata(toJson(data));
    }

    /**
     * 投影诊断报告
     */
    public String projectDiagnosis(AgentDiagnosisService.DiagnosisReport report,
                                   long elapsedMs,
                                   int toolCallCount,
                                   int failedToolCount,
                                   double quotaConsumed,
                                   int replanCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("elapsedMs", elapsedMs);
        metrics.put("toolCallCount", toolCallCount);
        metrics.put("failedToolCount", failedToolCount);
        metrics.put("quotaConsumed", quotaConsumed);
        metrics.put("replanCount", replanCount);
        metrics.put("toolSuccessRate", toolCallCount == 0 ? 1.0d :
                (double) (toolCallCount - failedToolCount) / toolCallCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", report.getLevel().name());
        data.put("summary", report.getSummary());
        data.put("issues", projectDiagnosisIssues(report.getIssues()));
        data.put("metrics", metrics);

        return AgentResponse.diagnosis(report.getLevel().name(), toJson(data));
    }

    /**
     * 投影运行完成
     */
    public String projectRunDone(AcademicAgentRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRunId());
        data.put("sessionId", run.getSessionId());
        data.put("status", run.getStatus());
        data.put("durationMs", run.getDurationMillis());
        return AgentResponse.metadata(toJson(data));
    }

    /**
     * 投影引用资料
     */
    public String projectReference(List<Map<String, Object>> references) {
        if (references == null || references.isEmpty()) {
            return AgentResponse.reference("[]", 0);
        }
        return AgentResponse.reference(toJson(references), references.size());
    }

    /**
     * 投影工具调用
     */
    public String projectToolCall(String toolName, String toolInput) {
        return AgentResponse.tool(nullToBlank(toolName), nullToBlank(toolInput));
    }

    /**
     * 投影工具结果
     */
    public String projectToolResult(String toolName, String toolOutput) {
        return AgentResponse.toolResult(nullToBlank(toolName), nullToBlank(toolOutput));
    }

    /**
     * 投影错误
     */
    public String projectError(String code, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", nullToBlank(code));
        data.put("message", nullToBlank(message));
        return AgentResponse.error(toJson(data));
    }

    // ========== 私有辅助方法 ==========

    private List<Map<String, Object>> projectPlanSteps(AcademicAgentPlan plan) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AcademicPlanStep step : plan.getSteps()) {
            Map<String, Object> stepData = new LinkedHashMap<>();
            stepData.put("stepId", step.getStepId());
            stepData.put("instruction", step.getInstruction());
            stepData.put("order", step.getOrder());
            stepData.put("status", step.getStatus());
            steps.add(stepData);
        }
        return steps;
    }

    private List<Map<String, Object>> projectDiagnosisIssues(
            List<AgentDiagnosisService.DiagnosisItem> issues) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentDiagnosisService.DiagnosisItem issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", issue.getLevel().name());
            item.put("code", issue.getCode());
            item.put("message", issue.getMessage());
            result.add(item);
        }
        return result;
    }

    private String nullToBlank(String str) {
        return str == null ? "" : str;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
