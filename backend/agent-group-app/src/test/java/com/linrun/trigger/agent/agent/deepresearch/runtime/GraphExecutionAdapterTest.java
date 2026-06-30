package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.trigger.agent.entity.OverAllState;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.TaskResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExecutionAdapterTest {

    @Test
    void shouldExecuteDeepGraphInOrder() {
        List<String> visited = new ArrayList<>();
        GraphExecutionAdapter adapter = new GraphExecutionAdapter(
                context -> visited.add("plan"),
                context -> visited.add("execute"),
                context -> visited.add("review"),
                context -> visited.add("final")
        );
        AgentRunContext context = AgentRunContext.builder()
                .mode("deep")
                .state(new OverAllState("S10001", "研究 Spring AI Alibaba Graph"))
                .sink(Sinks.many().unicast().onBackpressureBuffer())
                .finished(new AtomicBoolean(false))
                .thinkingBuffer(new StringBuilder())
                .build();

        adapter.execute(context);

        assertEquals(List.of("plan", "execute", "review", "final"), visited);
    }

    @Test
    void deepGraphWorkerContextCarriesMatchingSkills() {
        List<String> loaded = new ArrayList<>();
        GraphExecutionAdapter adapter = new GraphExecutionAdapter(
                context -> context.availableSkills(List.of(new SkillRuntimeDescriptor(
                        "deep-report",
                        "Deep report skill",
                        List.of("deep"),
                        List.of("deep"),
                        List.of(),
                        "",
                        List.of(),
                        "1.0",
                        true,
                        List.of("report_writer"),
                        List.of()))),
                context -> loaded.addAll(context.availableSkills().stream()
                        .map(SkillRuntimeDescriptor::name)
                        .toList()),
                context -> {
                },
                context -> {
                }
        );
        AgentRunContext context = AgentRunContext.builder()
                .mode("deep")
                .taskType("deep")
                .state(new OverAllState("S10001", "生成研究报告"))
                .sink(Sinks.many().unicast().onBackpressureBuffer())
                .finished(new AtomicBoolean(false))
                .thinkingBuffer(new StringBuilder())
                .build();

        adapter.execute(context);

        assertEquals(List.of("deep-report"), loaded);
    }

    @Test
    void agentRunContextBuildsRoleScopedViews() {
        AgentRunContext context = AgentRunContext.builder()
                .userId("U1001")
                .sessionId("S1001")
                .runId("R1001")
                .traceId("T1001")
                .spanId("SP1001")
                .mode("deep")
                .taskType("research")
                .state(new OverAllState("S10001", "研究动态技能"))
                .sink(Sinks.many().unicast().onBackpressureBuffer())
                .finished(new AtomicBoolean(false))
                .thinkingBuffer(new StringBuilder())
                .availableSkills(List.of(new SkillRuntimeDescriptor(
                        "deep-report",
                        "Deep report skill",
                        List.of("deep"),
                        List.of("research"),
                        List.of("topic"),
                        "markdown report",
                        List.of("workspace-read"),
                        "1.0",
                        true,
                        List.of("read_skill"),
                        List.of("skills/deep-report/SKILL.md"))))
                .build();
        context.currentPlan(List.of(new PlanTask("T1", "检索资料", 1)));
        Map<String, TaskResult> results = new LinkedHashMap<>();
        results.put("T1", new TaskResult("T1", false, null, "工具超时"));
        context.currentResults(results);

        assertTrue(context.plannerContext().includedSections().contains("goal"));
        assertFalse(context.plannerContext().includedSections().contains("currentPlan"));
        assertTrue(context.workerContext(List.of("read_skill")).includedSections().contains("registeredTools"));
        assertTrue(context.reviewerContext().includedSections().contains("failedTasks"));
        assertEquals(List.of("T1"), context.reviewerContext().data().get("failedTasks"));
    }
}
