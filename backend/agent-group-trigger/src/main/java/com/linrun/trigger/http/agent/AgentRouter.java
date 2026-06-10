package com.linrun.trigger.http.agent;

import com.linrun.domain.academic.runtime.mode.AgentModeSelector;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentRouter {

    public RoutingResult route(String requestedTaskType,
                               AgentModeSelector.ModeSelectionResult selection,
                               boolean webSearchEnabled,
                               boolean hasFile) {
        String agentType = normalizeAgentType(requestedTaskType, selection);
        List<String> agents = cooperatingAgents(agentType, webSearchEnabled, hasFile);
        String reason = switch (agentType) {
            case "deep" -> "深度任务需要资料收集、证据整理和结果输出";
            case "ppt" -> "PPT 任务按需求、资料、大纲和渲染流程推进";
            case "file" -> "文件任务优先读取材料并做引用回答";
            case "manual-skills", "skills" -> "Skill 任务由技能流程组合多个工具完成";
            case "image" -> "图像任务沿用图像生成工作区并记录产物";
            default -> webSearchEnabled ? "已开启联网搜索，按搜索增强对话执行" : "普通问题使用轻量对话 Agent";
        };
        return new RoutingResult(agentType, agents, reason);
    }

    private String normalizeAgentType(String requestedTaskType,
                                      AgentModeSelector.ModeSelectionResult selection) {
        String requested = requestedTaskType == null ? "" : requestedTaskType.trim();
        if (StringUtils.hasText(requested) && !"chat".equals(requested)) {
            return requested;
        }
        String selected = selection == null ? "" : selection.getAgentType();
        return switch (selected) {
            case "search" -> "chat";
            case "skill" -> "manual-skills";
            case "" -> "chat";
            default -> selected;
        };
    }

    private List<String> cooperatingAgents(String agentType, boolean webSearchEnabled, boolean hasFile) {
        return switch (agentType) {
            case "deep" -> webSearchEnabled
                    ? List.of("TaskPlannerAgent", "WebSearchAgent", "EvidenceAgent", "ReportAgent")
                    : List.of("TaskPlannerAgent", hasFile ? "FileAgent" : "ContextAgent", "ReportAgent");
            case "ppt" -> List.of("RequirementAgent", "OutlineAgent", webSearchEnabled ? "SearchAgent" : "MaterialAgent", "PptRenderAgent");
            case "file" -> List.of("FileAgent", "CitationAgent", "AnswerAgent");
            case "manual-skills", "skills" -> List.of("SkillSelectorAgent", "ToolExecutionAgent", "ArtifactAgent");
            case "image" -> List.of("ImagePromptAgent", "ImageGenerationAgent", "ArtifactAgent");
            default -> webSearchEnabled
                    ? List.of("ChatAgent", "WebSearchAgent", "AnswerAgent")
                    : List.of("ChatAgent", hasFile ? "FileAgent" : "AnswerAgent");
        };
    }

    public record RoutingResult(String agentType, List<String> selectedAgents, String reason) {

        public Map<String, Object> toEventData(String runId) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("runId", runId == null ? "" : runId);
            data.put("agentType", agentType == null ? "" : agentType);
            data.put("selectedAgents", selectedAgents == null ? List.of() : selectedAgents);
            data.put("routingReason", reason == null ? "" : reason);
            return data;
        }
    }
}
