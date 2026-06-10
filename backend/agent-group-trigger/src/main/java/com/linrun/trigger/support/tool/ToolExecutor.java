package com.linrun.trigger.support.tool;

import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);
    private final AgentObservabilityMetrics metrics;
    private final AcademicExecutionLedgerService ledgerService;

    public ToolExecutor() {
        this(AgentObservabilityMetrics.noop(), (AcademicExecutionLedgerService) null);
    }

    public ToolExecutor(AgentObservabilityMetrics metrics) {
        this(metrics, (AcademicExecutionLedgerService) null);
    }

    @Autowired
    public ToolExecutor(AgentObservabilityMetrics metrics,
                        ObjectProvider<AcademicExecutionLedgerService> ledgerServiceProvider) {
        this(metrics, ledgerServiceProvider == null ? null : ledgerServiceProvider.getIfAvailable());
    }

    private ToolExecutor(AgentObservabilityMetrics metrics,
                         AcademicExecutionLedgerService ledgerService) {
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
        this.ledgerService = ledgerService;
    }

    public <T> ToolExecution<T> execute(String toolName,
                                        String action,
                                        String successMessage,
                                        Supplier<T> supplier) {
        return execute(toolName, action, successMessage, 0, supplier);
    }

    public <T> ToolExecution<T> execute(String toolName,
                                        String action,
                                        String successMessage,
                                        int maxRetries,
                                        Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        String toolCallId = toolName + "-" + UUID.randomUUID();
        String ledgerInvocationId = recordToolStart(toolCallId, toolName, action);
        int attempts = Math.max(1, maxRetries + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = supplier.get();
                long latencyMillis = elapsedMillis(startNanos);
                metrics.recordToolExecution(toolName, action, true, latencyMillis);
                recordToolFinish(ledgerInvocationId, AcademicAgentRun.STATUS_SUCCESS,
                        successMessage, digest(result), attempt - 1, "", latencyMillis);
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
                recordToolFinish(ledgerInvocationId, AcademicAgentRun.STATUS_FAILED,
                        "tool execution failed", digest(e), attempt - 1, e.getMessage(), latencyMillis);
                return ToolExecution.failure(toolName, action,
                        "tool execution failed: " + e.getMessage(), latencyMillis, e,
                        toolCallId, attempt - 1, digest(e));
            }
        }
        long latencyMillis = elapsedMillis(startNanos);
        metrics.recordToolExecution(toolName, action, false, latencyMillis);
        recordToolFinish(ledgerInvocationId, AcademicAgentRun.STATUS_FAILED,
                "tool execution failed", digest(lastException), attempts - 1,
                lastException == null ? "" : lastException.getMessage(), latencyMillis);
        return ToolExecution.failure(toolName, action, "tool execution failed", latencyMillis, lastException,
                toolCallId, attempts - 1, digest(lastException));
    }

    private String recordToolStart(String toolCallId, String toolName, String action) {
        if (ledgerService == null) {
            return "";
        }
        return ledgerService.recordToolStart(AcademicLedgerContext.current(),
                toolCallId, toolName, action, "{}");
    }

    private void recordToolFinish(String invocationId,
                                  String status,
                                  String resultSummary,
                                  String resultJson,
                                  int retryCount,
                                  String errorMessage,
                                  long latencyMillis) {
        if (ledgerService == null) {
            return;
        }
        ledgerService.recordToolFinish(invocationId, status, resultSummary, resultJson,
                retryCount, errorMessage, latencyMillis);
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















