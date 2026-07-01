package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AgentStreamRequest;
import com.linrun.domain.agent.runtime.mode.AgentModeSelector;
import com.linrun.domain.agent.runtime.reasoning.AgentReasoningService;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class UnifiedAgentOrchestrator {

    public static final String AUTO_TASK_TYPE = "auto";

    private final AgentModeSelector modeSelector;
    private final AgentRouter agentRouter;

    public UnifiedAgentOrchestrator() {
        this(new AgentModeSelector(), new AgentRouter());
    }

    public UnifiedAgentOrchestrator(AgentModeSelector modeSelector, AgentRouter agentRouter) {
        this.modeSelector = modeSelector == null ? new AgentModeSelector() : modeSelector;
        this.agentRouter = agentRouter == null ? new AgentRouter() : agentRouter;
    }

    public OrchestrationPlan plan(String question,
                                  String requestedTaskType,
                                  String fileIds,
                                  boolean webSearchEnabled,
                                  AgentStreamRequest request) {
        AgentModeSelector.ModeSelectionContext context = selectionContext(requestedTaskType, fileIds, request);
        AgentModeSelector.ModeSelectionResult selection = modeSelector.selectMode(question, context);
        AgentRouter.RoutingResult routing = agentRouter.route(requestedTaskType, selection,
                webSearchEnabled, StringUtils.hasText(fileIds));
        return new OrchestrationPlan(selection, routing);
    }

    private AgentModeSelector.ModeSelectionContext selectionContext(String requestedTaskType,
                                                                    String fileIds,
                                                                    AgentStreamRequest request) {
        boolean hasFile = StringUtils.hasText(fileIds);
        String imageUrl = request == null ? "" : request.getImageUrl();
        String attachmentType = hasFile ? (StringUtils.hasText(imageUrl) ? "image" : "file") : "";
        if (isExplicitTaskType(requestedTaskType)) {
            return new AgentModeSelector.ModeSelectionContext(hasFile, attachmentType, true, requestedTaskType);
        }
        if (hasFile) {
            return AgentModeSelector.ModeSelectionContext.withAttachment(attachmentType);
        }
        return AgentModeSelector.ModeSelectionContext.empty();
    }

    private boolean isExplicitTaskType(String requestedTaskType) {
        if (!StringUtils.hasText(requestedTaskType)) {
            return false;
        }
        String normalized = requestedTaskType.trim().toLowerCase(Locale.ROOT);
        return !"chat".equals(normalized) && !AUTO_TASK_TYPE.equals(normalized);
    }

    /**
     * 解析实际执行 agentType：auto 走编排路由；显式 chat 保持 chat；其他显式模式走 routing。
     */
    public static String resolveExecutionAgentType(String requestedTaskType, OrchestrationPlan plan) {
        String requested = normalizeTaskTypeKey(requestedTaskType);
        if (AUTO_TASK_TYPE.equals(requested)) {
            return routedAgentType(plan, "chat");
        }
        if ("chat".equals(requested)) {
            return "chat";
        }
        return routedAgentType(plan, requested);
    }

    public static Map<String, Object> executionAppliedData(String runId,
                                                           String requestedTaskType,
                                                           String executionAgentType,
                                                           OrchestrationPlan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", safe(runId));
        data.put("requestedTaskType", safe(requestedTaskType));
        data.put("executionAgentType", safe(executionAgentType));
        data.put("autoRouted", AUTO_TASK_TYPE.equals(normalizeTaskTypeKey(requestedTaskType)));
        if (plan != null && plan.modeSelection() != null) {
            data.put("executionMode", plan.modeSelection().getExecutionMode());
            data.put("modeFamily", plan.modeSelection().getModeFamily());
            data.put("reason", plan.modeSelection().getReason());
            data.put("summary", plan.modeSelection().getSummary());
        }
        return data;
    }

    private static String routedAgentType(OrchestrationPlan plan, String fallback) {
        if (plan != null && plan.routing() != null && StringUtils.hasText(plan.routing().agentType())) {
            return plan.routing().agentType().trim();
        }
        return fallback;
    }

    private static String normalizeTaskTypeKey(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return "chat";
        }
        return taskType.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record OrchestrationPlan(AgentModeSelector.ModeSelectionResult modeSelection,
                                    AgentRouter.RoutingResult routing) {

        public Map<String, Object> taskAnalysisData(String runId) {
            AgentReasoningService.TaskAnalysisResult analysis = modeSelection.getTaskAnalysis();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("runId", safe(runId));
            data.put("taskType", analysis.getTaskType());
            data.put("difficulty", analysis.getDifficulty());
            data.put("estimatedSteps", analysis.getEstimatedSteps());
            data.put("needsMultipleSources", analysis.needsMultipleSources());
            data.put("summary", analysis.getSummary());
            return data;
        }

        public Map<String, Object> modeSelectionData(String runId) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("runId", safe(runId));
            data.put("executionMode", modeSelection.getExecutionMode());
            data.put("modeFamily", modeSelection.getModeFamily());
            data.put("agentType", modeSelection.getAgentType());
            data.put("reason", modeSelection.getReason());
            data.put("summary", modeSelection.getSummary());
            return data;
        }

        public Map<String, Object> routingData(String runId) {
            return routing.toEventData(runId);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
