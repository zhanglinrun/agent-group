package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.trigger.agent.entity.OverAllState;
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
}
