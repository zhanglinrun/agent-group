package com.linrun.domain.support.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentObservabilityMetricsTest {

    @Test
    void recordAgentRunWritesCounterAndTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentObservabilityMetrics metrics = new AgentObservabilityMetrics(registry);

        metrics.recordAgentRun("deep", "SUCCESS", 1200L);
        metrics.recordAgentRun("deep", "SUCCESS", 800L);
        metrics.recordAgentRun("ppt", "FAILED", 300L);

        assertEquals(2D, registry.get("agent_group_agent_run_total")
                .tag("task_type", "deep").tag("status", "SUCCESS").counter().count());
        assertEquals(1D, registry.get("agent_group_agent_run_total")
                .tag("task_type", "ppt").tag("status", "FAILED").counter().count());
        assertEquals(2L, registry.get("agent_group_agent_run_latency")
                .tag("task_type", "deep").tag("status", "SUCCESS").timer().count());
    }

    @Test
    void recordLlmCallWritesModelLevelMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentObservabilityMetrics metrics = new AgentObservabilityMetrics(registry);

        metrics.recordLlmCall("qwen3.7-plus", "SUCCESS", false, 950L);
        metrics.recordLlmCall("qwen3.7-plus", "FAILED", true, 30000L);

        assertEquals(1D, registry.get("agent_group_llm_call_total")
                .tag("model", "qwen3.7-plus").tag("status", "SUCCESS").tag("fallback", "false").counter().count());
        assertEquals(1D, registry.get("agent_group_llm_call_total")
                .tag("model", "qwen3.7-plus").tag("status", "FAILED").tag("fallback", "true").counter().count());
        assertEquals(1L, registry.get("agent_group_llm_call_latency")
                .tag("model", "qwen3.7-plus").tag("status", "SUCCESS").timer().count());
    }

    @Test
    void recordTokenUsageSeparatesPromptAndCompletion() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentObservabilityMetrics metrics = new AgentObservabilityMetrics(registry);

        metrics.recordTokenUsage("agent", 500L, 700L);

        assertEquals(500D, registry.get("agent_group_token_usage")
                .tag("scene", "agent").tag("type", "prompt").summary().totalAmount());
        assertEquals(700D, registry.get("agent_group_token_usage")
                .tag("scene", "agent").tag("type", "completion").summary().totalAmount());
    }

    @Test
    void noopInstanceIsSafeToCall() {
        AgentObservabilityMetrics metrics = AgentObservabilityMetrics.noop();
        assertDoesNotThrow(() -> {
            metrics.recordAgentRun("deep", "SUCCESS", 100L);
            metrics.recordLlmCall("model", "SUCCESS", false, 100L);
            metrics.recordTokenUsage("agent", 1L, 2L);
        });
    }
}
