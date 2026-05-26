package com.linrun.infrastructure.knowledgeasset.vector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class KnowledgeVectorMetrics {

    private final MeterRegistry meterRegistry;

    @Autowired
    public KnowledgeVectorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public static KnowledgeVectorMetrics noop() {
        return new KnowledgeVectorMetrics((MeterRegistry) null);
    }

    public void recordPgvectorSave(boolean success, long latencyMillis) {
        increment("agent_group_vector_pgvector_save_total", "status", status(success));
        recordTimer("agent_group_vector_pgvector_save_latency", latencyMillis);
    }

    public void recordPgvectorSearch(boolean success, long latencyMillis) {
        increment("agent_group_vector_pgvector_search_total", "status", status(success));
        recordTimer("agent_group_vector_pgvector_search_latency", latencyMillis);
    }

    public void recordVectorIssue(String reason) {
        increment("agent_group_vector_issue_total", "reason", reason);
    }

    private void increment(String name, String tagName, String tagValue) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(name).tag(tagName, tagValue).register(meterRegistry).increment();
    }

    private void recordTimer(String name, long latencyMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(name).register(meterRegistry).record(Duration.ofMillis(Math.max(0L, latencyMillis)));
    }

    private String status(boolean success) {
        return success ? "success" : "failed";
    }
}
