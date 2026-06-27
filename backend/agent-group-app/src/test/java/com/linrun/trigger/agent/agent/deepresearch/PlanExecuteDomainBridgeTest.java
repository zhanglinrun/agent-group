package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecuteDomainBridgeTest {

    @Test
    void shouldBuildRetryTasksForTimeoutFailure() {
        PlanExecuteDomainBridge bridge = new PlanExecuteDomainBridge();
        List<PlanTask> plan = List.of(
                new PlanTask("T1", "收集资料", 1),
                new PlanTask("T2", "整理报告", 2));
        Map<String, TaskResult> results = Map.of(
                "T1", new TaskResult("T1", true, "done", null),
                "T2", new TaskResult("T2", false, null, "tool timeout while fetching"));

        Optional<List<PlanTask>> retryTasks = bridge.buildRetryTasks(plan, results, 0);

        assertTrue(retryTasks.isPresent());
        assertFalse(retryTasks.get().isEmpty());
        assertTrue(retryTasks.get().stream().anyMatch(task -> task.instruction().contains("整理报告")));
    }

    @Test
    void shouldReflectLowQualityWhenMostStepsFailed() {
        PlanExecuteDomainBridge bridge = new PlanExecuteDomainBridge();
        List<PlanTask> plan = List.of(
                new PlanTask("T1", "步骤一", 1),
                new PlanTask("T2", "步骤二", 2));
        Map<String, TaskResult> results = Map.of(
                "T1", new TaskResult("T1", false, null, "parameter invalid"),
                "T2", new TaskResult("T2", false, null, "parameter invalid"));

        var reflection = bridge.reflect(plan, results);

        assertTrue(reflection.needReplan());
        assertTrue(reflection.getQuality() < 0.7D);
    }
}
