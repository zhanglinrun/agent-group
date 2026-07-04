package com.linrun.domain.agent.runtime.mode;

import com.linrun.domain.agent.runtime.reasoning.AgentReasoningService;

import java.util.Locale;

/**
 * Agent 执行策略选择器。
 * ReAct 和 Plan-Execute 是当前主 Agent 架构；PPT 与 Skill 是业务编排路线，不作为标准 Agent 架构模式包装。
 */
public class AgentModeSelector {

    private final AgentReasoningService reasoningService;

    public AgentModeSelector(AgentReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    public AgentModeSelector() {
        this.reasoningService = new AgentReasoningService();
    }

    /**
     * 选择 Agent 执行策略。
     */
    public ModeSelectionResult selectMode(String question, ModeSelectionContext context) {
        if (question == null || question.trim().isEmpty()) {
            return ModeSelectionResult.fallback();
        }
        ModeSelectionContext safeContext = context == null ? ModeSelectionContext.empty() : context;

        // 使用推理服务分析任务
        AgentReasoningService.TaskAnalysisResult analysis = 
                reasoningService.analyzeTask(question);

        // 根据上下文优先选择
        if (safeContext.isExplicitMode()) {
            return selectByExplicitMode(safeContext.getExplicitMode(), analysis);
        }

        if (safeContext.hasAttachment()) {
            if ("image".equals(safeContext.getAttachmentType())) {
                return ModeSelectionResult.image(analysis);
            }
            return ModeSelectionResult.fileReact(analysis);
        }

        if (isSkillInvocation(question)) {
            return ModeSelectionResult.skillsReact(analysis);
        }

        // 根据任务类型自动选择
        String taskType = analysis.getTaskType();
        if (taskType.contains("深度分析") || taskType.contains("研究")) {
            return ModeSelectionResult.planExecute(analysis);
        }

        if (taskType.contains("PPT") || taskType.contains("演示") || taskType.contains("幻灯片")) {
            return ModeSelectionResult.pptFlow(analysis);
        }

        if (taskType.contains("搜索") || taskType.contains("查找") || taskType.contains("检索")) {
            return ModeSelectionResult.webSearchReact(analysis);
        }

        // 默认使用 ReAct 模式
        return ModeSelectionResult.react(analysis);
    }

    private boolean isSkillInvocation(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        if (question.contains("调用") && question.contains("技能")) {
            return true;
        }
        return lower.contains("调用技能")
                || lower.contains("调用 skill")
                || lower.contains("invoke skill")
                || lower.contains("run skill")
                || lower.contains("use skill")
                || lower.contains("技能编排")
                || lower.contains("skill orchestration")
                || lower.matches(".*\\bskill\\b.*");
    }

    private ModeSelectionResult selectByExplicitMode(String mode, 
            AgentReasoningService.TaskAnalysisResult analysis) {
        switch (normalizeMode(mode)) {
            case "deep":
            case "research":
                return ModeSelectionResult.planExecute(analysis);
            case "ppt":
            case "flow":
            case "ppt-workflow":
                return ModeSelectionResult.pptFlow(analysis);
            case "search":
                return ModeSelectionResult.webSearchReact(analysis);
            case "skill":
            case "skills":
            case "manual-skills":
            case "skill-sop":
            case "skill-orchestration":
                return ModeSelectionResult.skillsReact(analysis);
            case "image":
                return ModeSelectionResult.image(analysis);
            default:
                return ModeSelectionResult.react(analysis);
        }
    }

    private String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 模式选择上下文
     */
    public static class ModeSelectionContext {
        private final boolean hasAttachment;
        private final String attachmentType;
        private final boolean explicitMode;
        private final String explicitModeValue;

        public ModeSelectionContext(boolean hasAttachment, String attachmentType,
                                   boolean explicitMode, String explicitModeValue) {
            this.hasAttachment = hasAttachment;
            this.attachmentType = attachmentType != null ? attachmentType : "";
            this.explicitMode = explicitMode;
            this.explicitModeValue = explicitModeValue != null ? explicitModeValue : "";
        }

        public static ModeSelectionContext simple(String explicitMode) {
            return new ModeSelectionContext(false, "", explicitMode != null && !explicitMode.isBlank(), explicitMode);
        }

        public static ModeSelectionContext withAttachment(String attachmentType) {
            return new ModeSelectionContext(true, attachmentType, false, "");
        }

        public static ModeSelectionContext empty() {
            return new ModeSelectionContext(false, "", false, "");
        }

        public boolean hasAttachment() {
            return hasAttachment;
        }

        public String getAttachmentType() {
            return attachmentType;
        }

        public boolean isExplicitMode() {
            return explicitMode;
        }

        public String getExplicitMode() {
            return explicitModeValue;
        }
    }

    /**
     * 模式选择结果
     */
    public static class ModeSelectionResult {
        private final String agentType;
        private final String executionMode;
        private final String modeFamily;
        private final String reason;
        private final AgentReasoningService.TaskAnalysisResult taskAnalysis;

        public ModeSelectionResult(String agentType, String executionMode, String modeFamily,
                                  String reason, AgentReasoningService.TaskAnalysisResult taskAnalysis) {
            this.agentType = agentType;
            this.executionMode = executionMode;
            this.modeFamily = modeFamily;
            this.reason = reason;
            this.taskAnalysis = taskAnalysis;
        }

        public static ModeSelectionResult react(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "chat",
                    "ReAct",
                    "react",
                    "适合快速问答和简单任务",
                    analysis
            );
        }

        public static ModeSelectionResult fileReact(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "file",
                    "ReAct",
                    "react",
                    "文件上传场景，使用文件问答 Agent",
                    analysis
            );
        }

        public static ModeSelectionResult planExecute(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "deep",
                    "Plan-Execute",
                    "plan-execute",
                    "需要多步骤规划和依赖编排",
                    analysis
            );
        }

        public static ModeSelectionResult pptFlow(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "ppt",
                    "PPT Workflow",
                    "ppt-workflow",
                    "PPT 生成按需求澄清、大纲、素材和渲染路线推进",
                    analysis
            );
        }

        public static ModeSelectionResult webSearchReact(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "search",
                    "ReAct",
                    "react",
                    "需要联网搜索",
                    analysis
            );
        }

        public static ModeSelectionResult skillsReact(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "skill",
                    "Skill Orchestration",
                    "skill-orchestration",
                    "调用预定义技能并组合工具完成任务",
                    analysis
            );
        }

        public static ModeSelectionResult image(AgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "image",
                    "ReAct",
                    "react",
                    "图像任务通过提示词整理和图像工具调用完成",
                    analysis
            );
        }

        public static ModeSelectionResult fallback() {
            return new ModeSelectionResult(
                    "chat",
                    "ReAct",
                    "react",
                    "默认模式",
                    AgentReasoningService.TaskAnalysisResult.empty()
            );
        }

        public String getAgentType() {
            return agentType;
        }

        public String getExecutionMode() {
            return executionMode;
        }

        public String getModeFamily() {
            return modeFamily;
        }

        public String getReason() {
            return reason;
        }

        public AgentReasoningService.TaskAnalysisResult getTaskAnalysis() {
            return taskAnalysis;
        }

        public String getSummary() {
            return String.format("[%s] %s - %s", executionMode, agentType, reason);
        }
    }
}
