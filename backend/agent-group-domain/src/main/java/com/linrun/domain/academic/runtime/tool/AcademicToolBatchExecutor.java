package com.linrun.domain.academic.runtime.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.academic.runtime.executor.AcademicAgentExecutorSupport;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputProjector;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

public class AcademicToolBatchExecutor {

    private final ObjectMapper objectMapper;
    private final AcademicExecutionLedgerService ledgerService;

    public AcademicToolBatchExecutor(ObjectMapper objectMapper,
                                     AcademicExecutionLedgerService ledgerService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.ledgerService = ledgerService;
    }

    public List<AcademicToolCallResult> executeAll(AcademicToolCollection collection,
                                                   List<AcademicToolCallCommand> commands,
                                                   Executor executor,
                                                   AcademicLedgerContext.Context ledgerContext) {
        if (collection == null) {
            throw new IllegalArgumentException("tool collection cannot be null");
        }
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<IndexedResult>> futures = IntStream.range(0, commands.size())
                .mapToObj(index -> executeAsync(collection, commands.get(index), executor, ledgerContext, index))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(IndexedResult::index))
                .map(IndexedResult::result)
                .toList();
    }

    private CompletableFuture<IndexedResult> executeAsync(AcademicToolCollection collection,
                                                          AcademicToolCallCommand command,
                                                          Executor executor,
                                                          AcademicLedgerContext.Context ledgerContext,
                                                          int index) {
        return AcademicAgentExecutorSupport.supplyAsync(executor, "工具批量调用",
                        () -> new IndexedResult(index, executeOne(collection, command, ledgerContext)))
                .exceptionally(throwable -> new IndexedResult(index, rejectedResult(command, throwable)));
    }

    private AcademicToolCallResult executeOne(AcademicToolCollection collection,
                                              AcademicToolCallCommand command,
                                              AcademicLedgerContext.Context ledgerContext) {
        long startedAt = System.nanoTime();
        AcademicToolCallCommand safeCommand = command == null
                ? AcademicToolCallCommand.builder("").build()
                : command;
        AcademicLedgerContext.Context context = resolveContext(safeCommand, ledgerContext);
        String toolCallId = safeCommand.getToolName() + "-" + UUID.randomUUID();
        String ledgerInvocationId = recordToolStart(context, toolCallId, safeCommand);
        try {
            AcademicToolCallResult result = collection.call(safeCommand);
            recordToolFinish(context, ledgerInvocationId, result, elapsedMillis(startedAt));
            return result;
        } catch (AppException e) {
            AcademicToolCallResult result = AcademicToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), elapsedMillis(startedAt)).build();
            recordToolFinish(context, ledgerInvocationId, result, result.getLatencyMillis());
            return result;
        } catch (Exception e) {
            AcademicToolCallResult result = AcademicToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), "TOOL_EXECUTE_FAILED",
                    e.getMessage(), elapsedMillis(startedAt)).build();
            recordToolFinish(context, ledgerInvocationId, result, result.getLatencyMillis());
            return result;
        }
    }

    private AcademicToolCallResult rejectedResult(AcademicToolCallCommand command, Throwable throwable) {
        AcademicToolCallCommand safeCommand = command == null
                ? AcademicToolCallCommand.builder("").build()
                : command;
        if (throwable instanceof AppException e) {
            return AcademicToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), 0L).build();
        }
        Throwable cause = throwable == null ? null : throwable.getCause();
        if (cause instanceof AppException e) {
            return AcademicToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), 0L).build();
        }
        String message = cause == null ? "" : cause.getMessage();
        return AcademicToolCallResult.failure(
                safeCommand.getToolName(), safeCommand.getAction(), "TOOL_ASYNC_FAILED", message, 0L).build();
    }

    private String recordToolStart(AcademicLedgerContext.Context context,
                                   String toolCallId,
                                   AcademicToolCallCommand command) {
        if (ledgerService == null) {
            return "";
        }
        return ledgerService.recordToolStart(context, toolCallId,
                command.getToolName(), command.getAction(), json(command.getArguments()));
    }

    private void recordToolFinish(AcademicLedgerContext.Context context,
                                  String invocationId,
                                  AcademicToolCallResult result,
                                  long latencyMillis) {
        if (ledgerService == null) {
            return;
        }
        String status = result.isSuccess() ? AcademicAgentRun.STATUS_SUCCESS : AcademicAgentRun.STATUS_FAILED;
        String resultJson = result.isSuccess()
                ? json(result.getResult())
                : json(Map.of("errorCode", result.getErrorCode(), "errorMessage", result.getErrorMessage()));
        String summary = result.isSuccess() ? summarize(result.getResult()) : result.getErrorMessage();
        ledgerService.recordToolFinish(invocationId, status, summary, resultJson,
                0, result.isSuccess() ? "" : result.getErrorMessage(), latencyMillis);
        if (result.isSuccess() && hasArtifactRefs(result)) {
            ledgerService.recordToolArtifacts(context, invocationId, result.getToolName(), result.getResult());
        }
    }

    private boolean hasArtifactRefs(AcademicToolCallResult result) {
        if (result == null || result.getResult().isEmpty()) {
            return false;
        }
        if (!result.getArtifactIds().isEmpty()) {
            return true;
        }
        return AcademicToolOutputProjector.hasArtifactReferences(result.getResult());
    }

    private AcademicLedgerContext.Context resolveContext(AcademicToolCallCommand command,
                                                         AcademicLedgerContext.Context fallback) {
        if (fallback != null) {
            return fallback;
        }
        if (!StringUtils.hasText(command.getRunId())) {
            return AcademicLedgerContext.current();
        }
        return new AcademicLedgerContext.Context(
                command.getRunId(), command.getRequestId(), command.getSessionId(), command.getUserId(), "tool_batch");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String summarize(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        String readableSummary = firstText(result.get("summary"), result.get("title"), result.get("content"));
        if (StringUtils.hasText(readableSummary)) {
            return readableSummary.length() <= 180 ? readableSummary : readableSummary.substring(0, 180);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        result.entrySet().stream()
                .limit(5)
                .forEach(entry -> summary.put(entry.getKey(), entry.getValue()));
        String text = json(summary);
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record IndexedResult(int index, AcademicToolCallResult result) {
    }
}
