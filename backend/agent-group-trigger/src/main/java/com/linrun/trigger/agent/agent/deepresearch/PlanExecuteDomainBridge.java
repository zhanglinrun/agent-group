package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowReplanRequest;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentIntelligentReplanStrategy;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentReflectionService;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 domain 层重规划 / 反思能力接入线上 PlanExecuteAgent。
 */
public class PlanExecuteDomainBridge {

    private final AcademicAgentIntelligentReplanStrategy replanStrategy = new AcademicAgentIntelligentReplanStrategy();
    private final AcademicAgentReflectionService reflectionService = new AcademicAgentReflectionService();

    public boolean hasFailures(List<PlanTask> plan, Map<String, TaskResult> results) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id())) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            if (result == null || !result.success()) {
                return true;
            }
        }
        return false;
    }

    public Optional<List<PlanTask>> buildRetryTasks(List<PlanTask> plan,
                                                    Map<String, TaskResult> results,
                                                    int replanCount) {
        if (plan == null || plan.isEmpty()) {
            return Optional.empty();
        }
        PlanTask failedTask = null;
        TaskResult failedResult = null;
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id())) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            if (result == null || !result.success()) {
                failedTask = task;
                failedResult = result;
                break;
            }
        }
        if (failedTask == null) {
            return Optional.empty();
        }

        AcademicAgentPlan agentPlan = toAgentPlan(plan, results);
        AcademicPlanStep failedStep = toFailedStep(failedTask, failedResult);
        List<AcademicPlanStep> completedSteps = completedSteps(plan, results, failedTask.id());
        String failureNote = failedResult == null
                ? "unknown error"
                : StringUtils.hasText(failedResult.error()) ? failedResult.error() : "step failed";

        List<AcademicPlanStep> replanned = replanStrategy.replan(new AcademicAgentFlowReplanRequest(
                agentPlan,
                failedStep,
                AcademicAgentStepExecutionResult.failed(failureNote),
                completedSteps,
                replanCount));
        if (replanned == null || replanned.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toPlanTasks(replanned));
    }

    public AcademicAgentReflectionService.ReflectionResult reflect(List<PlanTask> plan,
                                                                   Map<String, TaskResult> results) {
        AcademicAgentPlan agentPlan = toAgentPlan(plan, results);
        List<AcademicPlanStep> observed = observedSteps(agentPlan, results);
        return reflectionService.reflect(agentPlan, observed);
    }

    public Map<String, Object> reflectionPayload(int round,
                                                 AcademicAgentReflectionService.ReflectionResult reflection) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "reflection");
        payload.put("source", "domain_rule");
        payload.put("round", Math.max(1, round));
        boolean passed = reflection != null && !reflection.needReplan();
        payload.put("passed", passed);
        payload.put("quality", reflection == null ? 0D : reflection.getQuality());
        payload.put("feedback", reflection == null ? "" : reflection.getSummary());
        payload.put("action", passed ? "summarize" : "replan");
        return payload;
    }

    private AcademicAgentPlan toAgentPlan(List<PlanTask> plan, Map<String, TaskResult> results) {
        List<AcademicPlanStep> steps = new ArrayList<>();
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id())) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            AcademicPlanStep.Builder builder = AcademicPlanStep.builder(task.id(), task.instruction())
                    .order(Math.max(1, task.order()))
                    .assignedAgent("executor");
            if (result != null && result.success()) {
                builder.status(AcademicPlanLifecycleService.STATUS_COMPLETED)
                        .note(result.output());
            } else if (result != null) {
                builder.status(AcademicPlanLifecycleService.STATUS_BLOCKED)
                        .note(result.error());
            } else {
                builder.status(AcademicPlanLifecycleService.STATUS_NOT_STARTED);
            }
            steps.add(builder.build());
        }
        return new AcademicAgentPlan("plan-execute", steps);
    }

    private List<AcademicPlanStep> observedSteps(AcademicAgentPlan plan, Map<String, TaskResult> results) {
        List<AcademicPlanStep> observed = new ArrayList<>();
        if (plan == null) {
            return observed;
        }
        for (AcademicPlanStep step : plan.getSteps()) {
            if (step == null) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(step.getStepId());
            if (result == null) {
                continue;
            }
            AcademicPlanStep copy = step.copy();
            if (result.success()) {
                copy.setStatus(AcademicPlanLifecycleService.STATUS_COMPLETED);
                copy.setNote(result.output());
            } else {
                copy.setStatus(AcademicPlanLifecycleService.STATUS_BLOCKED);
                copy.setNote(result.error());
            }
            observed.add(copy);
        }
        return observed;
    }

    private AcademicPlanStep toFailedStep(PlanTask task, TaskResult result) {
        return AcademicPlanStep.builder(task.id(), task.instruction())
                .order(Math.max(1, task.order()))
                .assignedAgent("executor")
                .status(AcademicPlanLifecycleService.STATUS_BLOCKED)
                .note(result == null ? "unknown error" : result.error())
                .build();
    }

    private List<AcademicPlanStep> completedSteps(List<PlanTask> plan,
                                                  Map<String, TaskResult> results,
                                                  String failedTaskId) {
        List<AcademicPlanStep> completed = new ArrayList<>();
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id()) || task.id().equals(failedTaskId)) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            if (result != null && result.success()) {
                completed.add(AcademicPlanStep.builder(task.id(), task.instruction())
                        .order(Math.max(1, task.order()))
                        .status(AcademicPlanLifecycleService.STATUS_COMPLETED)
                        .note(result.output())
                        .build());
            }
        }
        return completed;
    }

    private List<PlanTask> toPlanTasks(List<AcademicPlanStep> steps) {
        List<PlanTask> tasks = new ArrayList<>();
        for (AcademicPlanStep step : steps) {
            if (step == null || !StringUtils.hasText(step.getStepId())) {
                continue;
            }
            tasks.add(new PlanTask(step.getStepId(), step.getInstruction(), Math.max(1, step.getOrder())));
        }
        return tasks;
    }
}
