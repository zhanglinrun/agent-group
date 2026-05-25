package com.linrun.trigger.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    @Test
    void shouldRecordToolExecutionMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ToolExecutor executor = new ToolExecutor(new AgentObservabilityMetrics(meterRegistry));

        ToolExecution<String> success = executor.execute("knowledge_search", "execute", "ok", () -> "done");
        ToolExecution<String> failure = executor.execute("group_trial", "execute", "ok", () -> {
            throw new IllegalStateException("unavailable");
        });

        assertTrue(success.isSuccess());
        assertFalse(failure.isSuccess());
        assertEquals(1D, meterRegistry.get("agent_group_tool_call_total")
                .tag("tool", "knowledge_search")
                .tag("status", "success")
                .counter()
                .count());
        assertEquals(1D, meterRegistry.get("agent_group_tool_call_total")
                .tag("tool", "group_trial")
                .tag("status", "failed")
                .counter()
                .count());
        assertEquals(1L, meterRegistry.get("agent_group_tool_call_latency")
                .tag("tool", "knowledge_search")
                .timer()
                .count());
    }
}
