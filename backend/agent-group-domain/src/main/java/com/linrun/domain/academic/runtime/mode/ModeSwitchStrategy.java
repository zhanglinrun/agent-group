package com.linrun.domain.academic.runtime.mode;

import java.util.List;

/**
 * 模式动态切换策�?
 * 根数据任务特征自动选择最优执行模开
 */
public class ModeSwitchStrategy {

    private final ExecutionModeRegistry registry;

    public ModeSwitchStrategy(ExecutionModeRegistry registry) {
        this.registry = registry;
    }

    /**
     * 选择最优执行模开
     */
    public AgentExecutionMode selectMode(String userQuery, List<Object> attachments) {
        AgentExecutionMode.ExecutionContext context = new AgentExecutionMode.ExecutionContext(
                null, null, userQuery, attachments, null
        );
        return selectMode(context);
    }

    /**
     * 选择最优执行模式（完整上下文）
     */
    public AgentExecutionMode selectMode(AgentExecutionMode.ExecutionContext context) {
        String userQuery = context.getUserQuery();
        
        // 1. 文件上传 �?ReAct 模式（优先级：高）
        if (context.hasAttachments()) {
            return registry.getMode("react")
                    .orElse(registry.selectMode(context));
        }
        
        // 2. 包含"深度研究"。调研"关键读�?Plan-Execute 模式（优先级：高）
        if (containsDeepResearchKeywords(userQuery)) {
            return registry.getMode("plan-execute")
                    .orElse(registry.selectMode(context));
        }
        
        // 3. 包含"生成 PPT"。制作幻灯版 �?Flow 模式（优先级：中）
        if (containsPPTKeywords(userQuery)) {
            return registry.getMode("flow")
                    .orElse(registry.selectMode(context));
        }
        
        // 4. 技能调�?�?Skill-SOP 模式（优先级：中）
        if (isSkillInvocation(userQuery)) {
            return registry.getMode("skill-sop")
                    .orElse(registry.selectMode(context));
        }
        
        // 5. 默认 �?ReAct 模式
        return registry.getMode("react")
                .orElse(registry.selectMode(context));
    }

    /**
     * 检查是否包含深度研究关键词
     */
    private boolean containsDeepResearchKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String lower = query.toLowerCase();
        return lower.contains("深度研究") 
            || lower.contains("深度调研")
            || lower.contains("全面分析")
            || lower.contains("系系统研究")
            || (lower.contains("研究") && lower.length() > 20);
    }

    /**
     * 检查是否包�?PPT 关键读
     */
    private boolean containsPPTKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String lower = query.toLowerCase();
        return lower.contains("ppt") 
            || lower.contains("幻灯版)
            || lower.contains("演示文稿")
            || lower.contains("powerpoint");
    }

    /**
     * 检查是否是技能调�?
     */
    private boolean isSkillInvocation(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String lower = query.toLowerCase();
        return lower.startsWith("/") 
            || lower.contains("执行技能)
            || lower.contains("运行技能);
    }

    /**
     * 获取模式建议（用于调试）
     */
    public ModeSuggestion suggestMode(String userQuery, List<Object> attachments) {
        AgentExecutionMode.ExecutionContext context = new AgentExecutionMode.ExecutionContext(
                null, null, userQuery, attachments, null
        );
        
        AgentExecutionMode selected = selectMode(context);
        String reason = explainSelection(context, selected);
        
        return new ModeSuggestion(selected.modeName(), reason);
    }

    /**
     * 解释选择原因
     */
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

    /**
     * 模式建议结果
     */
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















