package com.linrun.domain.agent.runtime.executor;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutorSupportTest {

    @Test
    void shouldMapRejectedSupplyToBusyException() {
        Executor rejected = command -> {
            throw new RejectedExecutionException("full");
        };

        CompletableFuture<String> future = AgentExecutorSupport.supplyAsync(
                rejected, "工具并发", () -> "ok");

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        AppException appException = assertInstanceOf(AppException.class, exception.getCause());
        assertEquals(AgentExecutorSupport.BUSY_CODE, appException.getCode());
    }

    @Test
    void shouldMapRejectedExecuteToBusyException() {
        Executor rejected = command -> {
            throw new RejectedExecutionException("full");
        };

        AppException exception = assertThrows(AppException.class,
                () -> AgentExecutorSupport.execute(rejected, "回放投影", () -> {
                }));

        assertEquals(AgentExecutorSupport.BUSY_CODE, exception.getCode());
    }
}















