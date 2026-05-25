package com.linrun.trigger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);
    private final AgentObservabilityMetrics metrics;

    public ToolExecutor() {
        this(AgentObservabilityMetrics.noop());
    }

    @Autowired
    public ToolExecutor(AgentObservabilityMetrics metrics) {
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
    }

    public <T> ToolExecution<T> execute(String toolName,
                                        String action,
                                        String successMessage,
                                        Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        try {
            T result = supplier.get();
            long latencyMillis = elapsedMillis(startNanos);
            metrics.recordToolExecution(toolName, action, true, latencyMillis);
            return ToolExecution.success(toolName, action, successMessage, latencyMillis, result);
        } catch (Exception e) {
            long latencyMillis = elapsedMillis(startNanos);
            metrics.recordToolExecution(toolName, action, false, latencyMillis);
            LOGGER.warn("tool execute failed, toolName={}, action={}, reason={}",
                    toolName, action, e.getClass().getSimpleName());
            return ToolExecution.failure(toolName, action,
                    "工具执行失败：" + e.getMessage(), latencyMillis, e);
        }
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
