package com.linrun.domain.academic.runtime.mode;

import com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService;

/**
 * Agent 模式选择器
 * 根据用户问题和上下文自动选择最优执行模式
 */
public class AgentModeSelector {

    private final AcademicAgentReasoningService reasoningService;

    public AgentModeSelector(AcademicAgentReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    public AgentModeSelector() {
        this.reasoningService = new AcademicAgentReasoningService();
    }

    /**
     * 选择 Agent 执行模式
     */
    public ModeSelectionResult selectMode(String question, ModeSelectionContext context) {
        if (question == null || question.trim().isEmpty()) {
            return ModeSelectionResult.fallback();
        }

        // 使用推理服务分析任务
        AcademicAgentReasoningService.TaskAnalysisResult analysis = 
                reasoningService.analyzeTask(question);

        // 根据上下文优先选择
        if (context.hasAttachment()) {
            if (context.getAttachmentType().equals("image")) {
                return ModeSelectionResult.image(analysis);
            }
            return ModeSelectionResult.fileReact(analysis);
        }

        if (context.isExplicitMode()) {
            return selectByExplicitMode(context.getExplicitMode(), analysis);
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

        if (question.toLowerCase().contains("skill") 
                || question.contains("技能") 
                || question.contains("调用")) {
            return ModeSelectionResult.skillsReact(analysis);
        }

        // 默认使用 ReAct 模式
        return ModeSelectionResult.react(analysis);
    }

    private ModeSelectionResult selectByExplicitMode(String mode, 
            AcademicAgentReasoningService.TaskAnalysisResult analysis) {
        switch (mode) {
            case "deep":
            case "research":
                return ModeSelectionResult.planExecute(analysis);
            case "ppt":
                return ModeSelectionResult.pptFlow(analysis);
            case "search":
                return ModeSelectionResult.webSearchReact(analysis);
            case "skill":
            case "manual-skills":
                return ModeSelectionResult.skillsReact(analysis);
            case "image":
                return ModeSelectionResult.image(analysis);
            default:
                return ModeSelectionResult.react(analysis);
        }
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
            return new ModeSelectionContext(false, "", explicitMode != null, explicitMode);
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
        private final AcademicAgentReasoningService.TaskAnalysisResult taskAnalysis;

        public ModeSelectionResult(String agentType, String executionMode, String modeFamily,
                                  String reason, AcademicAgentReasoningService.TaskAnalysisResult taskAnalysis) {
            this.agentType = agentType;
            this.executionMode = executionMode;
            this.modeFamily = modeFamily;
            this.reason = reason;
            this.taskAnalysis = taskAnalysis;
        }

        public static ModeSelectionResult react(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "chat",
                    "ReAct",
                    "react",
                    "适合快速问答和简单任务",
                    analysis
            );
        }

        public static ModeSelectionResult fileReact(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "file",
                    "ReAct",
                    "react",
                    "文件上传场景，使用文件问答 Agent",
                    analysis
            );
        }

        public static ModeSelectionResult planExecute(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "deep",
                    "Plan-Execute",
                    "plan-execute",
                    "需要多步骤规划和依赖编排",
                    analysis
            );
        }

        public static ModeSelectionResult pptFlow(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "ppt",
                    "Flow",
                    "flow",
                    "PPT 生成固定流程",
                    analysis
            );
        }

        public static ModeSelectionResult webSearchReact(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "search",
                    "ReAct",
                    "react",
                    "需要联网搜索",
                    analysis
            );
        }

        public static ModeSelectionResult skillsReact(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "skill",
                    "Skill-SOP",
                    "skill",
                    "调用预定义技能",
                    analysis
            );
        }

        public static ModeSelectionResult image(AcademicAgentReasoningService.TaskAnalysisResult analysis) {
            return new ModeSelectionResult(
                    "image",
                    "Flow",
                    "flow",
                    "图像生成流程",
                    analysis
            );
        }

        public static ModeSelectionResult fallback() {
            return new ModeSelectionResult(
                    "chat",
                    "ReAct",
                    "react",
                    "默认模式",
                    AcademicAgentReasoningService.TaskAnalysisResult.empty()
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

        public AcademicAgentReasoningService.TaskAnalysisResult getTaskAnalysis() {
            return taskAnalysis;
        }

        public String getSummary() {
            return String.format("[%s] %s - %s", executionMode, agentType, reason);
        }
    }
}
