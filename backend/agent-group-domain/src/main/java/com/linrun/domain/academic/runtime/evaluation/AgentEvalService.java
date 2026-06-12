package com.linrun.domain.academic.runtime.evaluation;

import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowExecutionService;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowProjector;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutor;
import com.linrun.domain.academic.runtime.mode.AgentModeSelector;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentIntelligentReplanStrategy;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 引擎离线评测。
 *
 * 对每条用例做两件事：
 * 1. 模式选择评测——把问题交给 {@link AgentModeSelector}，对比期望的 Agent 类型与执行模式；
 * 2. 执行链路评测——按任务分析结果生成计划，用确定性的步骤执行器跑完
 *    {@link AcademicAgentFlowExecutionService}；带失败注入的用例会在第二步失败一次，
 *    检验智能重规划能否恢复并完成全部步骤。
 *
 * 全程不调用大模型，结果稳定可复现，适合放在持续集成里守住引擎行为。
 */
public class AgentEvalService {

    private static final int MAX_REPLAN_ATTEMPTS = 2;

    private final AgentModeSelector modeSelector;

    public AgentEvalService() {
        this(new AgentModeSelector(new AcademicAgentReasoningService()));
    }

    public AgentEvalService(AgentModeSelector modeSelector) {
        this.modeSelector = modeSelector == null ? new AgentModeSelector() : modeSelector;
    }

    public AgentEvalReport evaluate(List<AgentEvalCase> cases) {
        List<AgentEvalCaseResult> results = new ArrayList<>();
        if (cases == null) {
            return new AgentEvalReport(results);
        }
        for (AgentEvalCase evalCase : cases) {
            if (evalCase == null || evalCase.question().isBlank()) {
                continue;
            }
            results.add(evaluateCase(evalCase));
        }
        return new AgentEvalReport(results);
    }

    private AgentEvalCaseResult evaluateCase(AgentEvalCase evalCase) {
        long startNanos = System.nanoTime();
        AgentModeSelector.ModeSelectionContext context = evalCase.hasAttachment()
                ? AgentModeSelector.ModeSelectionContext.withAttachment(evalCase.attachmentType())
                : AgentModeSelector.ModeSelectionContext.empty();
        AgentModeSelector.ModeSelectionResult selection = modeSelector.selectMode(evalCase.question(), context);

        boolean modeCorrect = evalCase.expectedAgentType().equals(selection.getAgentType())
                && evalCase.expectedExecutionMode().equals(selection.getExecutionMode());

        AcademicAgentPlan plan = buildPlan(evalCase, selection);
        AcademicAgentFlowExecutionResult executionResult = executePlan(evalCase, plan);

        long elapsedMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        return new AgentEvalCaseResult(
                evalCase.caseId(),
                evalCase.question(),
                evalCase.expectedAgentType(),
                selection.getAgentType(),
                evalCase.expectedExecutionMode(),
                selection.getExecutionMode(),
                selection.getTaskAnalysis() == null ? "" : selection.getTaskAnalysis().getTaskType(),
                modeCorrect,
                executionResult.isCompleted(),
                plan.getSteps().size(),
                executionResult.getReplanCount(),
                elapsedMillis);
    }

    private AcademicAgentPlan buildPlan(AgentEvalCase evalCase, AgentModeSelector.ModeSelectionResult selection) {
        int stepCount = 3;
        if (selection.getTaskAnalysis() != null) {
            stepCount = Math.max(2, Math.min(6, selection.getTaskAnalysis().getEstimatedSteps()));
        }
        List<String> instructions = new ArrayList<>();
        for (int index = 1; index <= stepCount; index++) {
            instructions.add("第" + index + "步：推进任务——" + evalCase.question());
        }
        return AcademicAgentPlan.create("eval:" + evalCase.caseId(), instructions);
    }

    private AcademicAgentFlowExecutionResult executePlan(AgentEvalCase evalCase, AcademicAgentPlan plan) {
        AtomicBoolean failureInjected = new AtomicBoolean(false);
        AcademicAgentStepExecutor stepExecutor = (step, context) -> {
            if (evalCase.simulateStepFailure()
                    && step.getOrder() == 2
                    && failureInjected.compareAndSet(false, true)) {
                String note = evalCase.failureNote().isBlank() ? "tool not found: simulated" : evalCase.failureNote();
                return AcademicAgentStepExecutionResult.failed(note);
            }
            return AcademicAgentStepExecutionResult.success("done: " + step.getStepId());
        };
        AcademicAgentFlowExecutionService executionService = new AcademicAgentFlowExecutionService(
                new AcademicAgentFlowProjector(), MAX_REPLAN_ATTEMPTS);
        return executionService.execute("eval-" + evalCase.caseId(), plan, stepExecutor,
                new AcademicAgentIntelligentReplanStrategy());
    }
}
