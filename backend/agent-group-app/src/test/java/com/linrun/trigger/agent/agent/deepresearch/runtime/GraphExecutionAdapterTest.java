package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.trigger.agent.entity.OverAllState;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
