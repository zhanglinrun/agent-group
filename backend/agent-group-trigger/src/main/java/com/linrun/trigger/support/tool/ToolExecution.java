package com.linrun.trigger.support.tool;

public class ToolExecution<T> {

    private final String toolName;
    private final String action;
    private final String status;
    private final String message;
    private final long latencyMillis;
    private final String toolCallId;
    private final int retryCount;
    private final String resultDigest;
    private final T result;
    private final Exception exception;

    private ToolExecution(String toolName,
                          String action,
                          String status,
                          String message,
                          long latencyMillis,
                          String toolCallId,
                          int retryCount,
                          String resultDigest,
                          T result,
                          Exception exception) {
        this.toolName = toolName;
        this.action = action;
        this.status = status;
        this.message = message;
        this.latencyMillis = latencyMillis;
        this.toolCallId = toolCallId;
        this.retryCount = retryCount;
        this.resultDigest = resultDigest;
        this.result = result;
        this.exception = exception;
    }

    public static <T> ToolExecution<T> success(String toolName, String action, String message,
                                               long latencyMillis, T result) {
        return success(toolName, action, message, latencyMillis, result, "", 0, "");
    }

    public static <T> ToolExecution<T> success(String toolName,
                                               String action,
                                               String message,
                                               long latencyMillis,
                                               T result,
                                               String toolCallId,
                                               int retryCount,
                                               String resultDigest) {
        return new ToolExecution<>(toolName, action, "success", message, latencyMillis,
                toolCallId, retryCount, resultDigest, result, null);
    }

    public static <T> ToolExecution<T> failure(String toolName, String action, String message,
                                               long latencyMillis, Exception exception) {
        return failure(toolName, action, message, latencyMillis, exception, "", 0, "");
    }

    public static <T> ToolExecution<T> failure(String toolName,
                                               String action,
                                               String message,
                                               long latencyMillis,
                                               Exception exception,
                                               String toolCallId,
                                               int retryCount,
                                               String resultDigest) {
        return new ToolExecution<>(toolName, action, "failed", message, latencyMillis,
                toolCallId, retryCount, resultDigest, null, exception);
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

    public String getToolCallId() {
        return toolCallId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getResultDigest() {
        return resultDigest;
    }

    public T getResult() {
        return result;
    }

    public Exception getException() {
        return exception;
    }
}
