package com.linrun.trigger.service;

public class ToolExecution<T> {

    private final String toolName;
    private final String action;
    private final String status;
    private final String message;
    private final long latencyMillis;
    private final T result;
    private final Exception exception;

    private ToolExecution(String toolName,
                          String action,
                          String status,
                          String message,
                          long latencyMillis,
                          T result,
                          Exception exception) {
        this.toolName = toolName;
        this.action = action;
        this.status = status;
        this.message = message;
        this.latencyMillis = latencyMillis;
        this.result = result;
        this.exception = exception;
    }

    public static <T> ToolExecution<T> success(String toolName, String action, String message,
                                               long latencyMillis, T result) {
        return new ToolExecution<>(toolName, action, "success", message, latencyMillis, result, null);
    }

    public static <T> ToolExecution<T> failure(String toolName, String action, String message,
                                               long latencyMillis, Exception exception) {
        return new ToolExecution<>(toolName, action, "failed", message, latencyMillis, null, exception);
    }

    public boolean isSuccess() {
        return "success".equals(status);
    }

    public String getToolName() {
        return toolName;
    }

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public T getResult() {
        return result;
    }

    public Exception getException() {
        return exception;
    }
}
