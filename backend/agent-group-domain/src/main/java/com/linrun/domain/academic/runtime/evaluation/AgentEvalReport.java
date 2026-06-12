package com.linrun.domain.academic.runtime.evaluation;

import java.util.List;
import java.util.Locale;

/**
 * 评测汇总报告：模式选择准确率、计划执行成功率、平均步数和重规划次数。
 *
 * 这些数字由 {@link AgentEvalService} 在本地确定性执行得出，
 * 可以通过单元测试反复复现，用来回答"怎么证明引擎行为符合预期"。
 */
public class AgentEvalReport {

    private final List<AgentEvalCaseResult> results;

    public AgentEvalReport(List<AgentEvalCaseResult> results) {
        this.results = results == null ? List.of() : List.copyOf(results);
    }

    public List<AgentEvalCaseResult> getResults() {
        return results;
    }

    public int getTotalCases() {
        return results.size();
    }

    public double getModeAccuracy() {
        if (results.isEmpty()) {
            return 0D;
        }
        long correct = results.stream().filter(AgentEvalCaseResult::modeCorrect).count();
        return (double) correct / results.size();
    }

    public double getFlowSuccessRate() {
        if (results.isEmpty()) {
            return 0D;
        }
        long completed = results.stream().filter(AgentEvalCaseResult::flowCompleted).count();
        return (double) completed / results.size();
    }

    public double getAveragePlanSteps() {
        return results.stream().mapToInt(AgentEvalCaseResult::planSteps).average().orElse(0D);
    }

    public double getAverageReplanCount() {
        return results.stream().mapToInt(AgentEvalCaseResult::replanCount).average().orElse(0D);
    }

    public long getReplanRecoveredCount() {
        return results.stream()
                .filter(result -> result.replanCount() > 0 && result.flowCompleted())
                .count();
    }

    public long getTotalElapsedMillis() {
        return results.stream().mapToLong(AgentEvalCaseResult::elapsedMillis).sum();
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("# Agent 引擎离线评测报告\n\n");
        builder.append("评测方式：本地确定性执行（模式选择 + 计划编排 + 注入失败的重规划恢复），不依赖大模型调用，可用 `mvn -pl agent-group-app -am test -Dtest=AgentEvalHarnessTest` 复现。\n\n");
        builder.append("## 汇总指标\n\n");
        builder.append("| 指标 | 数值 |\n|---|---|\n");
        builder.append(String.format(Locale.ROOT, "| 用例总数 | %d |%n", getTotalCases()));
        builder.append(String.format(Locale.ROOT, "| 模式选择准确率 | %.1f%% |%n", getModeAccuracy() * 100));
        builder.append(String.format(Locale.ROOT, "| 计划执行成功率 | %.1f%% |%n", getFlowSuccessRate() * 100));
        builder.append(String.format(Locale.ROOT, "| 平均计划步数 | %.2f |%n", getAveragePlanSteps()));
        builder.append(String.format(Locale.ROOT, "| 平均重规划次数 | %.2f |%n", getAverageReplanCount()));
        builder.append(String.format(Locale.ROOT, "| 注入失败后恢复成功的用例 | %d |%n", getReplanRecoveredCount()));
        builder.append(String.format(Locale.ROOT, "| 总耗时 | %d ms |%n", getTotalElapsedMillis()));
        builder.append("\n## 用例明细\n\n");
        builder.append("| 用例 | 期望模式 | 实际模式 | 模式正确 | 执行完成 | 步数 | 重规划 |\n");
        builder.append("|---|---|---|---|---|---|---|\n");
        for (AgentEvalCaseResult result : results) {
            builder.append(String.format(Locale.ROOT, "| %s | %s/%s | %s/%s | %s | %s | %d | %d |%n",
                    result.caseId(),
                    result.expectedAgentType(), result.expectedExecutionMode(),
                    result.actualAgentType(), result.actualExecutionMode(),
                    result.modeCorrect() ? "✓" : "✗",
                    result.flowCompleted() ? "✓" : "✗",
                    result.planSteps(),
                    result.replanCount()));
        }
        builder.append("\n说明：Token 消耗与模型延迟属于在线指标，由 `agent_group_token_usage`、`agent_group_llm_call_latency` 等 Prometheus 指标在真实运行时采集。\n");
        return builder.toString();
    }
}
