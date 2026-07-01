package com.linrun.domain.agent.runtime.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.ledger.service.AgentLedgerContext;
import com.linrun.domain.agent.runtime.executor.AgentExecutorSupport;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputProjector;
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

public class AgentToolBatchExecutor {

    private final ObjectMapper objectMapper;
    private final AgentExecutionLedgerService ledgerService;

    public AgentToolBatchExecutor(ObjectMapper objectMapper,
                                     AgentExecutionLedgerService ledgerService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.ledgerService = ledgerService;
    }

    public List<AgentToolCallResult> executeAll(AgentToolCollection collection,
                                                   List<AgentToolCallCommand> commands,
                                                   Executor executor,
                                                   AgentLedgerContext.Context ledgerContext) {
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

    private CompletableFuture<IndexedResult> executeAsync(AgentToolCollection collection,
                                                          AgentToolCallCommand command,
                                                          Executor executor,
                                                          AgentLedgerContext.Context ledgerContext,
                                                          int index) {
        return AgentExecutorSupport.supplyAsync(executor, "工具批量调用",
                        () -> new IndexedResult(index, executeOne(collection, command, ledgerContext)))
                .exceptionally(throwable -> new IndexedResult(index, rejectedResult(command, throwable)));
    }

    private AgentToolCallResult executeOne(AgentToolCollection collection,
                                              AgentToolCallCommand command,
                                              AgentLedgerContext.Context ledgerContext) {
        long startedAt = System.nanoTime();
        AgentToolCallCommand safeCommand = command == null
                ? AgentToolCallCommand.builder("").build()
                : command;
        AgentLedgerContext.Context context = resolveContext(safeCommand, ledgerContext);
        String toolCallId = safeCommand.getToolName() + "-" + UUID.randomUUID();
        String ledgerInvocationId = recordToolStart(context, toolCallId, safeCommand);
        try {
            AgentToolCallResult result = collection.call(safeCommand);
            recordToolFinish(context, ledgerInvocationId, result, elapsedMillis(startedAt));
            return result;
        } catch (AppException e) {
            AgentToolCallResult result = AgentToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), elapsedMillis(startedAt)).build();
            recordToolFinish(context, ledgerInvocationId, result, result.getLatencyMillis());
            return result;
        } catch (Exception e) {
            AgentToolCallResult result = AgentToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), "TOOL_EXECUTE_FAILED",
                    e.getMessage(), elapsedMillis(startedAt)).build();
            recordToolFinish(context, ledgerInvocationId, result, result.getLatencyMillis());
            return result;
        }
    }

    private AgentToolCallResult rejectedResult(AgentToolCallCommand command, Throwable throwable) {
        AgentToolCallCommand safeCommand = command == null
                ? AgentToolCallCommand.builder("").build()
                : command;
        if (throwable instanceof AppException e) {
            return AgentToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), 0L).build();
        }
        Throwable cause = throwable == null ? null : throwable.getCause();
        if (cause instanceof AppException e) {
            return AgentToolCallResult.failure(
                    safeCommand.getToolName(), safeCommand.getAction(), e.getCode(), e.getMessage(), 0L).build();
        }
        String message = cause == null ? "" : cause.getMessage();
        return AgentToolCallResult.failure(
                safeCommand.getToolName(), safeCommand.getAction(), "TOOL_ASYNC_FAILED", message, 0L).build();
    }

    private String recordToolStart(AgentLedgerContext.Context context,
                                   String toolCallId,
                                   AgentToolCallCommand command) {
        if (ledgerService == null) {
            return "";
        }
        return ledgerService.recordToolStart(context, toolCallId,
                command.getToolName(), command.getAction(), json(command.getArguments()));
    }

    private void recordToolFinish(AgentLedgerContext.Context context,
                                  String invocationId,
                                  AgentToolCallResult result,
                                  long latencyMillis) {
        if (ledgerService == null) {
            return;
        }
        String status = result.isSuccess() ? AgentRun.STATUS_SUCCESS : AgentRun.STATUS_FAILED;
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

    private boolean hasArtifactRefs(AgentToolCallResult result) {
        if (result == null || result.getResult().isEmpty()) {
            return false;
        }
        if (!result.getArtifactIds().isEmpty()) {
            return true;
        }
        return AgentToolOutputProjector.hasArtifactReferences(result.getResult());
    }

    private AgentLedgerContext.Context resolveContext(AgentToolCallCommand command,
                                                         AgentLedgerContext.Context fallback) {
        if (fallback != null) {
            return fallback;
        }
        if (!StringUtils.hasText(command.getRunId())) {
            return AgentLedgerContext.current();
        }
        return new AgentLedgerContext.Context(
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

    private record IndexedResult(int index, AgentToolCallResult result) {
    }
}















