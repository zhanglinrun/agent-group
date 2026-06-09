package com.linrun.domain.academic.runtime.diagnosis;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 异常诊断服务
 * 自动检测和诊断 Agent 执行中的异常情况
 */
public class AgentDiagnosisService {

    private static final long SLOW_EXECUTION_THRESHOLD_MS = 30000; // 30秒
    private static final double HIGH_QUOTA_THRESHOLD = 100.0;
    private static final int FREQUENT_REPLAN_THRESHOLD = 3;

    /**
     * 诊断 Agent 运行
     */
    public DiagnosisReport diagnose(AgentRunContext context) {
        List<DiagnosisItem> issues = new ArrayList<>();

        // 1. 检查执行耗时
        if (context.getElapsedMs() > SLOW_EXECUTION_THRESHOLD_MS) {
            issues.add(new DiagnosisItem(
                    DiagnosisLevel.WARNING,
                    "SLOW_EXECUTION",
                    String.format("执行耗时超过 %d 秒(实际: %d ms)", 
                            SLOW_EXECUTION_THRESHOLD_MS / 1000, 
                            context.getElapsedMs())
            ));
        }

        // 2. 检查工具调用失败
        if (context.getFailedToolCount() > 0) {
            issues.add(new DiagnosisItem(
                    DiagnosisLevel.ERROR,
                    "TOOL_FAILURE",
                    String.format("%d 个工具调用失败, context.getFailedToolCount())
            ));
        }

        // 3. 检查额度消耗
        if (context.getQuotaConsumed() > HIGH_QUOTA_THRESHOLD) {
            issues.add(new DiagnosisItem(
                    DiagnosisLevel.WARNING,
                    "HIGH_QUOTA",
                    String.format("额度消耗异常：%.2f", context.getQuotaConsumed())
            ));
        }

        // 4. 检查重规划次数
        if (context.getReplanCount() > FREQUENT_REPLAN_THRESHOLD) {
            issues.add(new DiagnosisItem(
                    DiagnosisLevel.WARNING,
                    "FREQUENT_REPLAN",
                    String.format("重规划次数过多：%d 次, context.getReplanCount())
            ));
        }

        // 5. 检查是否有异常
        if (context.hasException()) {
            issues.add(new DiagnosisItem(
                    DiagnosisLevel.ERROR,
                    "EXCEPTION",
                    "执行过程中出现异常 " + context.getExceptionMessage()
            ));
        }

        DiagnosisLevel overallLevel = determineOverallLevel(issues);
        return new DiagnosisReport(context.getRunId(), overallLevel, issues);
    }

    /**
     * 确定整体诊断级别
     */
    private DiagnosisLevel determineOverallLevel(List<DiagnosisItem> issues) {
        if (issues.isEmpty()) {
            return DiagnosisLevel.OK;
        }

        boolean hasError = issues.stream()
                .anyMatch(item -> item.getLevel() == DiagnosisLevel.ERROR);
        if (hasError) {
            return DiagnosisLevel.ERROR;
        }

        boolean hasWarning = issues.stream()
                .anyMatch(item -> item.getLevel() == DiagnosisLevel.WARNING);
        if (hasWarning) {
            return DiagnosisLevel.WARNING;
        }

        return DiagnosisLevel.OK;
    }

    /**
     * Agent 运行上下文（用于诊断）
     */
    public static class AgentRunContext {
        private final String runId;
        private final long elapsedMs;
        private final int failedToolCount;
        private final double quotaConsumed;
        private final int replanCount;
        private final boolean hasException;
        private final String exceptionMessage;

        public AgentRunContext(String runId, long elapsedMs, int failedToolCount,
                             double quotaConsumed, int replanCount,
                             boolean hasException, String exceptionMessage) {
            this.runId = runId;
            this.elapsedMs = elapsedMs;
            this.failedToolCount = failedToolCount;
            this.quotaConsumed = quotaConsumed;
            this.replanCount = replanCount;
            this.hasException = hasException;
            this.exceptionMessage = exceptionMessage;
        }

        public String getRunId() {
            return runId;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }

        public int getFailedToolCount() {
            return failedToolCount;
        }

        public double getQuotaConsumed() {
            return quotaConsumed;
        }

        public int getReplanCount() {
            return replanCount;
        }

        public boolean hasException() {
            return hasException;
        }

        public String getExceptionMessage() {
            return exceptionMessage;
        }
    }

    /**
     * 诊断报告
     */
    public static class DiagnosisReport {
        private final String runId;
        private final DiagnosisLevel level;
        private final List<DiagnosisItem> issues;

        public DiagnosisReport(String runId, DiagnosisLevel level, List<DiagnosisItem> issues) {
            this.runId = runId;
            this.level = level;
            this.issues = issues != null ? issues : new ArrayList<>();
        }

        public String getRunId() {
            return runId;
        }

        public DiagnosisLevel getLevel() {
            return level;
        }

        public List<DiagnosisItem> getIssues() {
            return issues;
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }

        public String getSummary() {
            if (issues.isEmpty()) {
                return "执行正常，未发现问题";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("发现 %d 个问题：\n", issues.size()));
            for (int i = 0; i < issues.size(); i++) {
                DiagnosisItem item = issues.get(i);
                sb.append(String.format("%d. [%s] %s: %s\n",
                        i + 1, item.getLevel(), item.getCode(), item.getMessage()));
            }
            return sb.toString();
        }
    }

    /**
     * 诊断项
     */
    public static class DiagnosisItem {
        private final DiagnosisLevel level;
        private final String code;
        private final String message;

        public DiagnosisItem(DiagnosisLevel level, String code, String message) {
            this.level = level;
            this.code = code;
            this.message = message;
        }

        public DiagnosisLevel getLevel() {
            return level;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 诊断级别
     */
    public enum DiagnosisLevel {
        OK, WARNING, ERROR
    }
}















