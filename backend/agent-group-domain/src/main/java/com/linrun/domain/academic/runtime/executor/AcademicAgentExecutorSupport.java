package com.linrun.domain.academic.runtime.executor;

import com.linrun.types.exception.AppException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public final class AcademicAgentExecutorSupport {

    public static final String BUSY_CODE = "AGENT_BUSY";
    public static final String BUSY_MESSAGE = "系系统繁忙，请稍后重试";

    private AcademicAgentExecutorSupport() {
    }

    public static <T> CompletableFuture<T> supplyAsync(Executor executor,
                                                       String scene,
                                                       Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        try {
            return CompletableFuture.supplyAsync(supplier, requireExecutor(executor, scene));
        } catch (RejectedExecutionException e) {
            return failedFuture(busy(scene));
        }
    }

    public static void execute(Executor executor, String scene, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable cannot be null");
        try {
            requireExecutor(executor, scene).execute(runnable);
        } catch (RejectedExecutionException e) {
            throw busy(scene);
        }
    }

    private static Executor requireExecutor(Executor executor, String scene) {
        if (executor == null) {
            throw new IllegalStateException("缺少执行器 " + safeScene(scene));
        }
        return executor;
    }

    private static AppException busy(String scene) {
        return new AppException(BUSY_CODE, safeScene(scene) + "执行器繁忙，" + BUSY_MESSAGE);
    }

    private static String safeScene(String scene) {
        return scene == null || scene.isBlank() ? "Agent" : scene.trim();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}















