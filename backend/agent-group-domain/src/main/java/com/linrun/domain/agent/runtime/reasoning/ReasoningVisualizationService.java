package com.linrun.domain.agent.runtime.reasoning;

import com.linrun.domain.agent.runtime.mode.AgentModeSelector;

/**
 * 推理过程可视化服务
 * 将 Agent 的推理过程转换为前端可展示的格式，类似 OpenAI o1 的思考链展示
 */
public class ReasoningVisualizationService {

    private final AgentReasoningService reasoningService;

    public ReasoningVisualizationService(AgentReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    public ReasoningVisualizationService() {
        this.reasoningService = new AgentReasoningService();
    }

    /**
     * 生成推理可视化内容
     */
    public String generateReasoningVisualization(String question, 
            AgentModeSelector.ModeSelectionResult modeSelection) {
        
        if (question == null || question.trim().isEmpty()) {
            return "";
        }

        StringBuilder visualization = new StringBuilder();
        
        AgentReasoningService.TaskAnalysisResult analysis = modeSelection.getTaskAnalysis();
        
        visualization.append("🤔 **任务分析**\n");
        visualization.append("- 类型：").append(analysis.getTaskType()).append("\n");
        visualization.append("- 难度：").append(analysis.getDifficulty()).append("\n");
        visualization.append("- 预估步骤：").append(analysis.getEstimatedSteps()).append(" 步\n");
        
        if (analysis.needsMultipleSources()) {
            visualization.append("- 需要多源对比：是\n");
        }
        
        visualization.append("\n");
        visualization.append("⚙️ **执行策略**\n");
        visualization.append("- 选择模式：").append(modeSelection.getExecutionMode()).append("\n");
        visualization.append("- 原因：").append(modeSelection.getReason()).append("\n");
        
        return visualization.toString();
    }

    /**
     * 生成简化版推理提示
     */
    public String generateQuickReasoningHint(AgentModeSelector.ModeSelectionResult modeSelection) {
        AgentReasoningService.TaskAnalysisResult analysis = modeSelection.getTaskAnalysis();
        
        return String.format("使用 %s 模式 | %s | 预计 %d 步",
                modeSelection.getExecutionMode(),
                analysis.getTaskType(),
                analysis.getEstimatedSteps());
    }

    /**
     * 为 Plan-Execute 模式生成推理步骤预览
     */
    public String generatePlanPreview(String question) {
        AgentReasoningService.TaskAnalysisResult analysis = 
                reasoningService.analyzeTask(question);
        
        StringBuilder preview = new StringBuilder();
        preview.append("📋 **计划预览**（基于任务分析）\n\n");
        
        int steps = analysis.getEstimatedSteps();
        String taskType = analysis.getTaskType();
        
        if (taskType.contains("深度分析") || taskType.contains("研究")) {
            preview.append("1. 需求澄清与主题确认\n");
            preview.append("2. 资料检索与信息收集\n");
            if (steps >= 4) {
                preview.append("3. 多源对比与证据整理\n");
                preview.append("4. 综合分析与结论输出\n");
            } else {
                preview.append("3. 综合分析与结论输出\n");
            }
            if (steps >= 5) {
                preview.append("5. 报告生成与格式化\n");
            }
        } else if (taskType.contains("信息检索")) {
            preview.append("1. 关键词提取与查询构建\n");
            preview.append("2. 信息检索与筛选\n");
            if (steps >= 3) {
                preview.append("3. 结果整理与输出\n");
            }
        } else {
            for (int i = 1; i <= steps; i++) {
                preview.append(i).append(". 步骤 ").append(i).append("\n");
            }
        }
        
        preview.append("\n💡 实际执行时会根据情况动态调整");
        
        return preview.toString();
    }
}
