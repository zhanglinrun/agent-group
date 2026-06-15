package com.linrun.domain.academic.runtime.mode;

import java.util.List;
import java.util.Locale;

/**
 * 根据任务特征自动选择执行模式。
 */
public class ModeSwitchStrategy {

    private final ExecutionModeRegistry registry;

    public ModeSwitchStrategy(ExecutionModeRegistry registry) {
        this.registry = registry;
    }

    public AgentExecutionMode selectMode(String userQuery, List<Object> attachments) {
        AgentExecutionMode.ExecutionContext context = new AgentExecutionMode.ExecutionContext(
                null, null, userQuery, attachments, null
        );
        return selectMode(context);
    }

    public AgentExecutionMode selectMode(AgentExecutionMode.ExecutionContext context) {
        if (context == null) {
            return registry.getMode("react")
                    .orElse(registry.selectMode(null));
        }
        String explicitMode = explicitMode(context);
        if (explicitMode != null) {
            return registry.getMode(explicitMode)
                    .orElse(registry.selectMode(context));
        }
        String userQuery = context.getUserQuery();

        if (context.hasAttachments()) {
            return registry.getMode("react")
                    .orElse(registry.selectMode(context));
        }

        if (isSkillInvocation(userQuery)) {
            return registry.getMode("skill-sop")
                    .orElse(registry.selectMode(context));
        }

        if (containsPPTKeywords(userQuery)) {
            return registry.getMode("flow")
                    .orElse(registry.selectMode(context));
        }

        if (containsSearchKeywords(userQuery) && !containsStrongPlanningKeywords(userQuery)) {
            return registry.getMode("react")
                    .orElse(registry.selectMode(context));
        }

        if (containsDeepResearchKeywords(userQuery)) {
            return registry.getMode("plan-execute")
                    .orElse(registry.selectMode(context));
        }

        return registry.getMode("react").orElse(registry.selectMode(context));
    }

    private boolean containsDeepResearchKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lower = query.toLowerCase();
        boolean strongPlanning = lower.contains("深度研究")
                || lower.contains("深度调研")
                || lower.contains("全面分析")
                || lower.contains("系统研究")
                || lower.contains("系统分析")
                || lower.contains("深入分析")
                || lower.contains("深入研究");
        boolean hasTaskAction = lower.contains("研究")
                || lower.contains("分析")
                || lower.contains("调研")
                || lower.contains("综述")
                || lower.contains("梳理")
                || lower.contains("总结");
        boolean hasAcademicFocus = lower.contains("对比")
                || lower.contains("比较")
                || lower.contains("现状")
                || lower.contains("趋势")
                || lower.contains("路线")
                || lower.contains("机制")
                || lower.contains("问题")
                || lower.contains("风险")
                || lower.contains("挑战")
                || lower.contains("流程")
                || lower.contains("多源")
                || lower.contains("近三年")
                || lower.contains("近年来");
        return strongPlanning || (hasTaskAction && hasAcademicFocus);
    }

    private boolean containsStrongPlanningKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lower = query.toLowerCase();
        return lower.contains("深度研究")
                || lower.contains("深度调研")
                || lower.contains("全面分析")
                || lower.contains("系统研究")
                || lower.contains("系统分析")
                || lower.contains("深入分析")
                || lower.contains("深入研究");
    }

    private boolean containsPPTKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lower = query.toLowerCase();
        return lower.contains("ppt")
                || lower.contains("幻灯片")
                || lower.contains("演示文稿")
                || lower.contains("powerpoint");
    }

    private boolean containsSearchKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lower = query.toLowerCase();
        return lower.contains("搜索")
                || lower.contains("查找")
                || lower.contains("检索")
                || lower.contains("查询");
    }

    private boolean isSkillInvocation(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String lower = query.toLowerCase();
        return lower.startsWith("/")
                || lower.contains("执行技能")
                || lower.contains("运行技能");
    }

    private String explicitMode(AgentExecutionMode.ExecutionContext context) {
        Object taskType = context.getMetadata("taskType");
        if (taskType == null) {
            taskType = context.getMetadata("explicitMode");
        }
        String normalized = normalizeMode(taskType == null ? "" : String.valueOf(taskType));
        if (normalized.isEmpty() || "chat".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "deep", "research" -> "plan-execute";
            case "ppt" -> "flow";
            case "skill", "skills", "manual-skills", "skill-sop" -> "skill-sop";
            default -> normalized;
        };
    }

    public ModeSuggestion suggestMode(String userQuery, List<Object> attachments) {
        AgentExecutionMode.ExecutionContext context = new AgentExecutionMode.ExecutionContext(
                null, null, userQuery, attachments, null
        );

        AgentExecutionMode selected = selectMode(context);
        String reason = explainSelection(context, selected);

        return new ModeSuggestion(selected.modeName(), reason);
    }

    private String explainSelection(AgentExecutionMode.ExecutionContext context,
                                    AgentExecutionMode selected) {
        if (context.hasAttachments()) {
            return "检测到文件上传，选择 ReAct 模式处理";
        }

        String query = context.getUserQuery();
        if (containsDeepResearchKeywords(query)) {
            return "检测到深度研究需求，选择 Plan-Execute 模式";
        }

        if (containsPPTKeywords(query)) {
            return "检测到 PPT 生成需求，选择 Flow 模式";
        }

        if (isSkillInvocation(query)) {
            return "检测到技能调用，选择 Skill-SOP 模式";
        }

        return "使用默认 ReAct 模式";
    }

    public static class ModeSuggestion {
        private final String modeName;
        private final String reason;

        public ModeSuggestion(String modeName, String reason) {
            this.modeName = modeName;
            this.reason = reason;
        }

        public String getModeName() {
            return modeName;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return String.format("建议模式: %s (%s)", modeName, reason);
        }
    }
}
