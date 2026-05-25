package com.linrun.trigger.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class AgentObservabilityMetrics {

    private final MeterRegistry meterRegistry;

    public AgentObservabilityMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider.getIfAvailable());
    }

    AgentObservabilityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    static AgentObservabilityMetrics noop() {
        return new AgentObservabilityMetrics((MeterRegistry) null);
    }

    void recordToolExecution(String toolName, String action, boolean success, long latencyMillis) {
        if (meterRegistry == null) {
            return;
        }
        String status = success ? "success" : "failed";
        Counter.builder("agent_group_tool_call_total")
                .tag("tool", normalizeTag(toolName))
                .tag("action", normalizeTag(action))
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        Timer.builder("agent_group_tool_call_latency")
                .tag("tool", normalizeTag(toolName))
                .tag("action", normalizeTag(action))
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, latencyMillis)));
    }

    void recordGuideUsage(long llmLatencyMillis,
                          long totalLatencyMillis,
                          long totalTokens,
                          BigDecimal estimatedCostYuan,
                          boolean fallbackUsed) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder("agent_group_guide_llm_latency")
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, llmLatencyMillis)));
        Timer.builder("agent_group_guide_total_latency")
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, totalLatencyMillis)));
        DistributionSummary.builder("agent_group_guide_token_usage")
                .register(meterRegistry)
                .record(Math.max(0L, totalTokens));
        DistributionSummary.builder("agent_group_guide_estimated_cost_yuan")
                .register(meterRegistry)
                .record(Math.max(0D, estimatedCostYuan == null ? 0D : estimatedCostYuan.doubleValue()));
        if (fallbackUsed) {
            Counter.builder("agent_group_guide_fallback_total")
                    .tag("reason", "llm_or_stream_fallback")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private String normalizeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
