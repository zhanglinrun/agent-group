package com.linrun.trigger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);

    public <T> ToolExecution<T> execute(String toolName,
                                        String action,
                                        String successMessage,
                                        Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        try {
            T result = supplier.get();
            return ToolExecution.success(toolName, action, successMessage, elapsedMillis(startNanos), result);
        } catch (Exception e) {
            LOGGER.warn("tool execute failed, toolName={}, action={}, reason={}",
                    toolName, action, e.getClass().getSimpleName());
            return ToolExecution.failure(toolName, action,
                    "工具执行失败：" + e.getMessage(), elapsedMillis(startNanos), e);
        }
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
