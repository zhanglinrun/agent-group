package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.domain.agent.runtime.agent.AgentFlowReplanRequest;
import com.linrun.domain.agent.runtime.agent.AgentPlan;
import com.linrun.domain.agent.runtime.agent.AgentStepExecutionResult;
import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleService;
import com.linrun.domain.agent.runtime.agent.AgentPlanStep;
import com.linrun.domain.agent.runtime.reasoning.AgentIntelligentReplanStrategy;
import com.linrun.domain.agent.runtime.reasoning.AgentReflectionService;
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

    private final AgentIntelligentReplanStrategy replanStrategy = new AgentIntelligentReplanStrategy();
    private final AgentReflectionService reflectionService = new AgentReflectionService();

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

        AgentPlan agentPlan = toAgentPlan(plan, results);
        AgentPlanStep failedStep = toFailedStep(failedTask, failedResult);
        List<AgentPlanStep> completedSteps = completedSteps(plan, results, failedTask.id());
        String failureNote = failedResult == null
                ? "unknown error"
                : StringUtils.hasText(failedResult.error()) ? failedResult.error() : "step failed";

        List<AgentPlanStep> replanned = replanStrategy.replan(new AgentFlowReplanRequest(
                agentPlan,
                failedStep,
                AgentStepExecutionResult.failed(failureNote),
                completedSteps,
                replanCount));
        if (replanned == null || replanned.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toPlanTasks(replanned));
    }

    public AgentReflectionService.ReflectionResult reflect(List<PlanTask> plan,
                                                                   Map<String, TaskResult> results) {
        AgentPlan agentPlan = toAgentPlan(plan, results);
        List<AgentPlanStep> observed = observedSteps(agentPlan, results);
        return reflectionService.reflect(agentPlan, observed);
    }

    public Map<String, Object> reflectionPayload(int round,
                                                 AgentReflectionService.ReflectionResult reflection) {
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

    private AgentPlan toAgentPlan(List<PlanTask> plan, Map<String, TaskResult> results) {
        List<AgentPlanStep> steps = new ArrayList<>();
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id())) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            AgentPlanStep.Builder builder = AgentPlanStep.builder(task.id(), task.instruction())
                    .order(Math.max(1, task.order()))
                    .assignedAgent("executor");
            if (result != null && result.success()) {
                builder.status(AgentPlanLifecycleService.STATUS_COMPLETED)
                        .note(result.output());
            } else if (result != null) {
                builder.status(AgentPlanLifecycleService.STATUS_BLOCKED)
                        .note(result.error());
            } else {
                builder.status(AgentPlanLifecycleService.STATUS_NOT_STARTED);
            }
            steps.add(builder.build());
        }
        return new AgentPlan("plan-execute", steps);
    }

    private List<AgentPlanStep> observedSteps(AgentPlan plan, Map<String, TaskResult> results) {
        List<AgentPlanStep> observed = new ArrayList<>();
        if (plan == null) {
            return observed;
        }
        for (AgentPlanStep step : plan.getSteps()) {
            if (step == null) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(step.getStepId());
            if (result == null) {
                continue;
            }
            AgentPlanStep copy = step.copy();
            if (result.success()) {
                copy.setStatus(AgentPlanLifecycleService.STATUS_COMPLETED);
                copy.setNote(result.output());
            } else {
                copy.setStatus(AgentPlanLifecycleService.STATUS_BLOCKED);
                copy.setNote(result.error());
            }
            observed.add(copy);
        }
        return observed;
    }

    private AgentPlanStep toFailedStep(PlanTask task, TaskResult result) {
        return AgentPlanStep.builder(task.id(), task.instruction())
                .order(Math.max(1, task.order()))
                .assignedAgent("executor")
                .status(AgentPlanLifecycleService.STATUS_BLOCKED)
                .note(result == null ? "unknown error" : result.error())
                .build();
    }

    private List<AgentPlanStep> completedSteps(List<PlanTask> plan,
                                                  Map<String, TaskResult> results,
                                                  String failedTaskId) {
        List<AgentPlanStep> completed = new ArrayList<>();
        for (PlanTask task : plan) {
            if (task == null || !StringUtils.hasText(task.id()) || task.id().equals(failedTaskId)) {
                continue;
            }
            TaskResult result = results == null ? null : results.get(task.id());
            if (result != null && result.success()) {
                completed.add(AgentPlanStep.builder(task.id(), task.instruction())
                        .order(Math.max(1, task.order()))
                        .status(AgentPlanLifecycleService.STATUS_COMPLETED)
                        .note(result.output())
                        .build());
            }
        }
        return completed;
    }

    private List<PlanTask> toPlanTasks(List<AgentPlanStep> steps) {
        List<PlanTask> tasks = new ArrayList<>();
        for (AgentPlanStep step : steps) {
            if (step == null || !StringUtils.hasText(step.getStepId())) {
                continue;
            }
            tasks.add(new PlanTask(step.getStepId(), step.getInstruction(), Math.max(1, step.getOrder())));
        }
        return tasks;
    }
}
