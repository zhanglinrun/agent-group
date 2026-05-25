package com.linrun.trigger.service;

import com.linrun.domain.conversation.model.AgentToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;
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
        return execute(toolName, action, successMessage, 0, supplier);
    }

    public <T> ToolExecution<T> execute(AgentToolDefinition definition,
                                        String action,
                                        String successMessage,
                                        Supplier<T> supplier) {
        if (definition == null) {
            return execute("unknown", action, successMessage, supplier);
        }
        return execute(definition.getName(), action, successMessage, definition.getMaxRetries(), supplier);
    }

    public <T> ToolExecution<T> execute(String toolName,
                                        String action,
                                        String successMessage,
                                        int maxRetries,
                                        Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        String toolCallId = toolName + "-" + UUID.randomUUID();
        int attempts = Math.max(1, maxRetries + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = supplier.get();
                long latencyMillis = elapsedMillis(startNanos);
                metrics.recordToolExecution(toolName, action, true, latencyMillis);
                return ToolExecution.success(toolName, action, successMessage, latencyMillis, result,
                        toolCallId, attempt - 1, digest(result));
            } catch (Exception e) {
                lastException = e;
                if (attempt < attempts) {
                    LOGGER.warn("tool execute retry, toolName={}, action={}, attempt={}, reason={}",
                            toolName, action, attempt, e.getClass().getSimpleName());
                    continue;
                }
                long latencyMillis = elapsedMillis(startNanos);
                metrics.recordToolExecution(toolName, action, false, latencyMillis);
                LOGGER.warn("tool execute failed, toolName={}, action={}, reason={}",
                        toolName, action, e.getClass().getSimpleName());
                return ToolExecution.failure(toolName, action,
                        "工具执行失败：" + e.getMessage(), latencyMillis, e,
                        toolCallId, attempt - 1, digest(e));
            }
        }
        long latencyMillis = elapsedMillis(startNanos);
        metrics.recordToolExecution(toolName, action, false, latencyMillis);
        return ToolExecution.failure(toolName, action, "工具执行失败", latencyMillis, lastException,
                toolCallId, attempts - 1, digest(lastException));
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String digest(Object value) {
        if (value == null) {
            return "";
        }
        String digest;
        if (value instanceof Collection<?> collection) {
            digest = "Collection(size=" + collection.size() + ")";
        } else if (value instanceof Exception exception) {
            digest = exception.getClass().getSimpleName() + ":" + exception.getMessage();
        } else {
            digest = value.toString();
        }
        return digest.length() <= 180 ? digest : digest.substring(0, 180);
    }
}
