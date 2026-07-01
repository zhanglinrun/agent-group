package com.linrun.domain.agent.runtime.reasoning;

/**
 * Agent 推理过程服务。
 */
public class AgentReasoningService {

    public TaskAnalysisResult analyzeTask(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return TaskAnalysisResult.empty();
        }

        String taskType = inferTaskType(userQuestion);
        int estimatedSteps = inferStepCount(userQuestion);
        if ("深度分析".equals(taskType)) {
            estimatedSteps = Math.max(estimatedSteps, 5);
        }
        boolean needsMultipleSources = inferNeedsMultipleSources(userQuestion);
        String difficulty = inferDifficulty(estimatedSteps);

        return new TaskAnalysisResult(taskType, estimatedSteps, needsMultipleSources, difficulty);
    }

    private String inferTaskType(String question) {
        String lower = question.toLowerCase();
        if (lower.contains("ppt") || question.contains("演示文稿") || question.contains("幻灯片")) {
            return "PPT 制作";
        }
        if (containsSearchIntent(lower) && !containsStrongPlanningIntent(lower)) {
            return "信息检索";
        }
        if (containsComplexAgentIntent(lower)) {
            return "深度分析";
        }
        if (containsSearchIntent(lower)) {
            return "信息检索";
        }
        if (lower.contains("生成") || lower.contains("创建") || lower.contains("制作")) {
            return "内容生成";
        }
        if (lower.contains("总结") || lower.contains("归纳")) {
            return "综合报告";
        }
        return "多步推理";
    }

    private int inferStepCount(String question) {
        int length = question.length();
        int steps;
        if (length < 20) {
            steps = 2;
        } else if (length < 50) {
            steps = 3;
        } else if (length < 100) {
            steps = 4;
        } else {
            steps = 5;
        }
        String lower = question.toLowerCase();
        if (containsComplexAgentIntent(lower) && !(containsSearchIntent(lower) && !containsStrongPlanningIntent(lower))) {
            steps = Math.max(steps, 4);
        }
        if (inferNeedsMultipleSources(question)) {
            steps = Math.max(steps, 5);
        }
        return steps;
    }

    private boolean inferNeedsMultipleSources(String question) {
        String lower = question.toLowerCase();
        return lower.contains("对比")
                || lower.contains("比较")
                || lower.contains("多个")
                || lower.contains("不同")
                || lower.contains("多源")
                || lower.contains("近三年")
                || lower.contains("近年来")
                || lower.contains("最新")
                || lower.contains("现状")
                || lower.contains("趋势")
                || lower.contains("路线")
                || lower.contains("综述")
                || lower.contains("调研")
                || lower.contains("文献")
                || lower.contains("论文");
    }

    private String inferDifficulty(int steps) {
        if (steps <= 2) {
            return "简单";
        }
        if (steps <= 4) {
            return "中等";
        }
        return "困难";
    }

    private boolean containsComplexAgentIntent(String lower) {
        boolean hasTaskAction = containsAny(lower, "研究", "调研", "分析", "综述", "梳理", "总结");
        boolean hasAgentFocus = containsAny(lower, "对比", "比较", "差异", "优劣")
                || containsAny(lower, "现状", "趋势", "路线", "机制", "框架", "体系", "方案")
                || containsAny(lower, "问题", "风险", "挑战", "流程");
        return containsDeepResearchIntent(lower) || (hasTaskAction && hasAgentFocus);
    }

    private boolean containsDeepResearchIntent(String lower) {
        return containsStrongPlanningIntent(lower)
                || (!containsSearchIntent(lower) && containsAny(lower, "调研", "综述"));
    }

    private boolean containsStrongPlanningIntent(String lower) {
        return containsAny(lower, "深度研究", "深度调研", "全面分析", "系统研究", "系统分析", "深入分析", "深入研究");
    }

    private boolean containsSearchIntent(String lower) {
        return containsAny(lower, "搜索", "查找", "检索", "查询");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static class TaskAnalysisResult {
        private final String taskType;
        private final int estimatedSteps;
        private final boolean needsMultipleSources;
        private final String difficulty;

        public TaskAnalysisResult(String taskType, int estimatedSteps,
                                  boolean needsMultipleSources, String difficulty) {
            this.taskType = taskType;
            this.estimatedSteps = estimatedSteps;
            this.needsMultipleSources = needsMultipleSources;
            this.difficulty = difficulty;
        }

        public static TaskAnalysisResult empty() {
            return new TaskAnalysisResult("未知", 3, false, "中等");
        }

        public String getTaskType() {
            return taskType;
        }

        public int getEstimatedSteps() {
            return estimatedSteps;
        }

        public boolean needsMultipleSources() {
            return needsMultipleSources;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public String getSummary() {
            return String.format("任务类型：%s | 预估步骤：%d | 难度：%s",
                    taskType, estimatedSteps, difficulty);
        }
    }
}
