package com.linrun.domain.academic.runtime.reasoning;

/**
 * Agent 推理过程服务。
 */
public class AcademicAgentReasoningService {

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
        if (lower.contains("研究") || lower.contains("调研") || lower.contains("分析")) {
            return "深度分析";
        }
        if (lower.contains("ppt") || question.contains("演示文稿") || question.contains("幻灯片")) {
            return "PPT 制作";
        }
        if (lower.contains("搜索") || lower.contains("查找") || lower.contains("检索")) {
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
        if (length < 20) {
            return 2;
        }
        if (length < 50) {
            return 3;
        }
        if (length < 100) {
            return 4;
        }
        return 5;
    }

    private boolean inferNeedsMultipleSources(String question) {
        String lower = question.toLowerCase();
        return lower.contains("对比")
                || lower.contains("比较")
                || lower.contains("多个")
                || lower.contains("不同");
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
