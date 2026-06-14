package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.domain.academic.runtime.mode.AgentModeSelector;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class UnifiedAgentOrchestrator {

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
                                  AcademicAgentStreamRequest request) {
        AgentModeSelector.ModeSelectionContext context = selectionContext(requestedTaskType, fileIds, request);
        AgentModeSelector.ModeSelectionResult selection = modeSelector.selectMode(question, context);
        AgentRouter.RoutingResult routing = agentRouter.route(requestedTaskType, selection,
                webSearchEnabled, StringUtils.hasText(fileIds));
        return new OrchestrationPlan(selection, routing);
    }

    private AgentModeSelector.ModeSelectionContext selectionContext(String requestedTaskType,
                                                                    String fileIds,
                                                                    AcademicAgentStreamRequest request) {
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
        return !"chat".equalsIgnoreCase(requestedTaskType.trim());
    }

    public record OrchestrationPlan(AgentModeSelector.ModeSelectionResult modeSelection,
                                    AgentRouter.RoutingResult routing) {

        public Map<String, Object> taskAnalysisData(String runId) {
            AcademicAgentReasoningService.TaskAnalysisResult analysis = modeSelection.getTaskAnalysis();
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
