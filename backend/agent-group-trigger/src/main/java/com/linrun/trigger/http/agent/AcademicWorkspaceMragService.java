package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicWorkspaceMragHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceMragRunRequest;
import com.linrun.api.dto.AcademicWorkspaceMragRunResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.academic.runtime.executor.AcademicAgentExecutorSupport;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.common.AcademicDeepSearchToolRuntime;
import com.linrun.domain.academic.runtime.tool.common.AcademicMultimodalAgentToolRuntime;
import com.linrun.domain.academic.runtime.tool.common.AcademicTableRagToolRuntime;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputProjector;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.model.TokenUsageMetrics;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

@Service
public class AcademicWorkspaceMragService {

    private static final String TASK_TYPE = "workspace-mrag";
    private static final String ACTION = "workspace/mrag/run";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AcademicMultimodalAnalysisPort> multimodalPort;
    private final ObjectProvider<AcademicTableRagPort> tableRagPort;
    private final ObjectProvider<AcademicDeepSearchPort> deepSearchPort;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AcademicAgentRepository academicAgentRepository;
    private final AcademicExecutionLedgerService ledgerService;
    private final Executor toolExecutor;

    @Autowired
    public AcademicWorkspaceMragService(ObjectMapper objectMapper,
                                        ObjectProvider<AcademicMultimodalAnalysisPort> multimodalPort,
                                        ObjectProvider<AcademicTableRagPort> tableRagPort,
                                        ObjectProvider<AcademicDeepSearchPort> deepSearchPort,
                                        UserAccountService userAccountService,
                                        UserQuotaService userQuotaService,
                                        AcademicAgentRepository academicAgentRepository,
                                        AcademicExecutionLedgerService ledgerService) {
        this(objectMapper, multimodalPort, tableRagPort, deepSearchPort, userAccountService,
                userQuotaService, academicAgentRepository, ledgerService, ForkJoinPool.commonPool());
    }

    AcademicWorkspaceMragService(ObjectMapper objectMapper,
                                 ObjectProvider<AcademicMultimodalAnalysisPort> multimodalPort,
                                 ObjectProvider<AcademicTableRagPort> tableRagPort,
                                 ObjectProvider<AcademicDeepSearchPort> deepSearchPort,
                                 UserAccountService userAccountService,
                                 UserQuotaService userQuotaService,
                                 AcademicAgentRepository academicAgentRepository,
                                 AcademicExecutionLedgerService ledgerService,
                                 Executor toolExecutor) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.multimodalPort = multimodalPort;
        this.tableRagPort = tableRagPort;
        this.deepSearchPort = deepSearchPort;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.academicAgentRepository = academicAgentRepository;
        this.ledgerService = ledgerService;
        this.toolExecutor = toolExecutor == null ? ForkJoinPool.commonPool() : toolExecutor;
    }

    public AcademicWorkspaceMragRunResponse run(String token, AcademicWorkspaceMragRunRequest request) {
        AcademicWorkspaceMragRunRequest safeRequest = request == null
                ? new AcademicWorkspaceMragRunRequest()
                : request;
        String question = firstText(safeRequest.getQuestion(), safeRequest.getText(), "MRAG 多模态检索问题");
        UserAccount user = userAccountService.requireUserByToken(token);
        String userId = user.getUserId();
        preCheckQuota(userId);
        String sessionId = firstText(safeRequest.getSessionId(), "MRAG" + System.currentTimeMillis());
        String requestId = "MRAGREQ" + UUID.randomUUID().toString().replace("-", "");
        saveSession(userId, sessionId, question);

        AcademicAgentRun run = ledgerService.startRun(
                userId, sessionId, "", requestId, TASK_TYPE, question, "workspace-mrag-tools");
        AcademicLedgerContext.Context context = new AcademicLedgerContext.Context(
                run.getRunId(), requestId, sessionId, userId, TASK_TYPE);
        long startedAt = System.currentTimeMillis();
        List<AcademicWorkspaceMragRunResponse.ToolResult> results = new ArrayList<>();
        List<String> missingTools = new ArrayList<>();
        try {
            List<ScheduledTool> scheduledTools = new ArrayList<>();
            AcademicMultimodalAnalysisPort multimodal = provider(multimodalPort);
            if (enabled(safeRequest.getIncludeMultimodal()) && multimodal != null) {
                scheduledTools.add(new ScheduledTool(AcademicToolOutputNames.MULTIMODAL_AGENT,
                        multimodalArguments(safeRequest, question),
                        new AcademicMultimodalAgentToolRuntime(multimodal)::call));
            } else if (enabled(safeRequest.getIncludeMultimodal())) {
                missingTools.add(AcademicToolOutputNames.MULTIMODAL_AGENT);
            }

            AcademicTableRagPort table = provider(tableRagPort);
            if (enabled(safeRequest.getIncludeTableRag()) && table != null) {
                scheduledTools.add(new ScheduledTool(AcademicToolOutputNames.TABLE_RAG,
                        tableRagArguments(requestId, safeRequest, question),
                        new AcademicTableRagToolRuntime(table)::call));
            } else if (enabled(safeRequest.getIncludeTableRag())) {
                missingTools.add(AcademicToolOutputNames.TABLE_RAG);
            }

            AcademicDeepSearchPort deepSearch = provider(deepSearchPort);
            if (enabled(safeRequest.getIncludeDeepSearch()) && deepSearch != null) {
                scheduledTools.add(new ScheduledTool(AcademicToolOutputNames.DEEP_SEARCH,
                        deepSearchArguments(safeRequest, question),
                        new AcademicDeepSearchToolRuntime(deepSearch)::call));
            } else if (enabled(safeRequest.getIncludeDeepSearch())) {
                missingTools.add(AcademicToolOutputNames.DEEP_SEARCH);
            }

            results.addAll(callTools(context, scheduledTools));
            if (results.isEmpty() && !missingTools.isEmpty()) {
                throw new AppException("MRAG_WORKSPACE_0001", "MRAG 工作区缺少可用工具：" + String.join(", ", missingTools));
            }

            String summary = summary(results, missingTools);
            long durationMillis = elapsed(startedAt);
            consumeQuota(userId, sessionId, requestId, durationMillis);
            ledgerService.finishRun(run, AcademicAgentRun.STATUS_SUCCESS, summary, "", "", durationMillis);
            updateSession(userId, sessionId, question, summary);
            return response(requestId, sessionId, run.getRunId(), summary, results, missingTools, safeRequest);
        } catch (AppException e) {
            ledgerService.finishRun(run, AcademicAgentRun.STATUS_FAILED, "", e.getCode(), e.getMessage(), elapsed(startedAt));
            throw e;
        } catch (Exception e) {
            ledgerService.finishRun(run, AcademicAgentRun.STATUS_FAILED, "", "MRAG_WORKSPACE_0002",
                    firstText(e.getMessage(), "MRAG 工作区执行失败"), elapsed(startedAt));
            throw new AppException("MRAG_WORKSPACE_0002", "MRAG 工作区执行失败：" + firstText(e.getMessage(), "未知错误"));
        }
    }

    private void preCheckQuota(String userId) {
        userQuotaService.assertEnoughQuota(userId, userQuotaService.estimatePreCheckCost(TASK_TYPE));
    }

    private void consumeQuota(String userId, String sessionId, String requestId, long durationMillis) {
        userQuotaService.consumeForAcademicTask(userId, sessionId, TASK_TYPE + "-" + requestId, TASK_TYPE,
                TokenUsageMetrics.empty(), TASK_TYPE + "-tools", durationMillis);
    }

    public AcademicWorkspaceMragHistoryResponse history(String token, String sessionId, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<AcademicAgentRun> runs = new ArrayList<>();
        if (StringUtils.hasText(sessionId)) {
            runs.addAll(ledgerService.queryRuns(user.getUserId(), sessionId.trim(), safeLimit));
        } else {
            for (AcademicSession session : academicAgentRepository.querySessions(user.getUserId(), safeLimit)) {
                runs.addAll(ledgerService.queryRuns(user.getUserId(), session.getSessionId(), safeLimit));
                if (runs.size() >= safeLimit) {
                    break;
                }
            }
        }
        List<AcademicWorkspaceMragHistoryResponse.Item> items = runs.stream()
                .filter(run -> TASK_TYPE.equals(run.getTaskType()))
                .limit(safeLimit)
                .map(this::historyItem)
                .toList();
        AcademicWorkspaceMragHistoryResponse response = new AcademicWorkspaceMragHistoryResponse();
        response.setSessionId(firstText(sessionId));
        response.setTotal(items.size());
        response.setItems(items);
        return response;
    }

    private List<AcademicWorkspaceMragRunResponse.ToolResult> callTools(AcademicLedgerContext.Context context,
                                                                        List<ScheduledTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<IndexedToolResult>> futures = IntStream.range(0, tools.size())
                .mapToObj(index -> AcademicAgentExecutorSupport.supplyAsync(toolExecutor, "workspace-mrag-tool",
                        () -> {
                            ScheduledTool tool = tools.get(index);
                            return new IndexedToolResult(index,
                                    callTool(context, tool.toolName(), tool.arguments(), tool.runtime()));
                        }))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingInt(IndexedToolResult::index))
                    .map(IndexedToolResult::result)
                    .toList();
        } catch (CompletionException e) {
            throw runtimeException(e);
        }
    }

    private RuntimeException runtimeException(CompletionException e) {
        Throwable cause = e == null ? null : e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new AppException("MRAG_WORKSPACE_TOOL_FAILED",
                firstText(cause == null ? "" : cause.getMessage(), "tool failed"));
    }

    private AcademicWorkspaceMragRunResponse.ToolResult callTool(AcademicLedgerContext.Context context,
                                                                 String toolName,
                                                                 Map<String, Object> arguments,
                                                                 ToolRuntime runtime) {
        String invocationId = ledgerService.recordToolStart(
                context,
                "workspace-mrag-" + toolName + "-" + context.requestId(),
                toolName,
                ACTION,
                json(arguments));
        long startedAt = System.currentTimeMillis();
        try {
            AcademicToolStructuredOutput output = runtime.call(AcademicToolCallCommand.builder(toolName)
                    .action(ACTION)
                    .requestId(context.requestId())
                    .sessionId(context.sessionId())
                    .userId(context.userId())
                    .runId(context.runId())
                    .arguments(arguments)
                    .build());
            Map<String, Object> result = AcademicToolOutputProjector.toResultMap(output);
            ledgerService.recordToolFinish(invocationId, AcademicAgentRun.STATUS_SUCCESS,
                    AcademicToolOutputProjector.summarize(output), json(result), 0, "", elapsed(startedAt));
            if (!output.getFileRefs().isEmpty()) {
                ledgerService.recordToolArtifacts(context, invocationId, output.getToolName(), result);
            }
            return toolResult(invocationId, output, result);
        } catch (Exception e) {
            ledgerService.recordToolFinish(invocationId, AcademicAgentRun.STATUS_FAILED,
                    "", "{}", 0, firstText(e.getMessage(), "tool failed"), elapsed(startedAt));
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AppException("MRAG_WORKSPACE_TOOL_FAILED", firstText(e.getMessage(), "tool failed"));
        }
    }

    private Map<String, Object> multimodalArguments(AcademicWorkspaceMragRunRequest request, String question) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("task", question);
        arguments.put("text", firstText(request.getText(), question));
        arguments.put("imageUrls", safeList(request.getImageUrls()));
        arguments.put("fileUrls", safeList(request.getFileUrls()));
        return arguments;
    }

    private Map<String, Object> tableRagArguments(String requestId,
                                                  AcademicWorkspaceMragRunRequest request,
                                                  String question) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("requestId", requestId);
        arguments.put("query", question);
        arguments.put("modelCodeList", safeList(request.getModelCodeList()));
        arguments.put("recallType", "mrag_recall");
        arguments.put("useVector", enabled(request.getUseVector()));
        arguments.put("useElastic", enabled(request.getUseElastic()));
        arguments.put("topK", Math.max(1, request.getTopK() == null ? 5 : request.getTopK()));
        return arguments;
    }

    private Map<String, Object> deepSearchArguments(AcademicWorkspaceMragRunRequest request, String question) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", question);
        arguments.put("maxResults", Math.max(1, request.getMaxResults() == null ? 5 : request.getMaxResults()));
        arguments.put("stream", false);
        arguments.put("sourceTypes", safeList(request.getSourceTypes()));
        arguments.put("options", request.getMetadata() == null ? Map.of() : request.getMetadata());
        return arguments;
    }

    private AcademicWorkspaceMragRunResponse.ToolResult toolResult(String invocationId,
                                                                   AcademicToolStructuredOutput output,
                                                                   Map<String, Object> resultMap) {
        AcademicWorkspaceMragRunResponse.ToolResult result = new AcademicWorkspaceMragRunResponse.ToolResult();
        result.setInvocationId(invocationId);
        result.setToolName(output.getToolName());
        result.setTitle(output.getTitle());
        result.setSummary(output.getSummary());
        result.setContent(output.getContent());
        result.setStructuredOutput(resultMap);
        result.setFileRefs(fileRefs(output.getFileRefs()));
        return result;
    }

    private List<Map<String, Object>> fileRefs(List<AcademicToolFileRef> refs) {
        return refs == null ? List.of() : refs.stream().map(AcademicToolFileRef::toMap).toList();
    }

    private AcademicWorkspaceMragRunResponse response(String requestId,
                                                      String sessionId,
                                                      String runId,
                                                      String summary,
                                                      List<AcademicWorkspaceMragRunResponse.ToolResult> results,
                                                      List<String> missingTools,
                                                      AcademicWorkspaceMragRunRequest request) {
        AcademicWorkspaceMragRunResponse response = new AcademicWorkspaceMragRunResponse();
        response.setRequestId(requestId);
        response.setSessionId(sessionId);
        response.setRunId(runId);
        response.setSummary(summary);
        response.setToolResults(results);
        response.setMissingTools(missingTools);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imageCount", request.getImageUrls() == null ? 0 : request.getImageUrls().size());
        metadata.put("fileCount", request.getFileUrls() == null ? 0 : request.getFileUrls().size());
        metadata.put("modelCodeList", safeList(request.getModelCodeList()));
        metadata.put("executedToolCount", results.size());
        response.setMetadata(metadata);
        return response;
    }

    private String summary(List<AcademicWorkspaceMragRunResponse.ToolResult> results, List<String> missingTools) {
        List<String> parts = results.stream()
                .map(item -> firstText(item.getSummary(), item.getTitle(), item.getToolName()))
                .filter(StringUtils::hasText)
                .toList();
        String summary = parts.isEmpty() ? "MRAG 工作区已完成工具运行" : String.join("；", parts);
        if (!missingTools.isEmpty()) {
            summary = summary + "；未配置工具: " + String.join(", ", missingTools);
        }
        return summary;
    }

    private AcademicWorkspaceMragHistoryResponse.Item historyItem(AcademicAgentRun run) {
        AcademicWorkspaceMragHistoryResponse.Item item = new AcademicWorkspaceMragHistoryResponse.Item();
        item.setRunId(run.getRunId());
        item.setSessionId(run.getSessionId());
        item.setQuestion(run.getQuestion());
        item.setSummary(run.getFinalSummary());
        item.setStatus(run.getStatus());
        item.setStartedAt(run.getStartedAt());
        item.setFinishedAt(run.getFinishedAt());
        item.setDurationMillis(run.getDurationMillis());
        return item;
    }

    private void saveSession(String userId, String sessionId, String question) {
        AcademicSession session = new AcademicSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(question, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(limit(question, 240));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(session.getCreateTime());
        try {
            academicAgentRepository.saveSessionIfAbsent(session);
        } catch (Exception ignored) {
        }
    }

    private void updateSession(String userId, String sessionId, String question, String summary) {
        AcademicSession session = new AcademicSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(question, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(firstText(limit(summary, 240), limit(question, 240)));
        session.setUpdateTime(LocalDateTime.now());
        try {
            academicAgentRepository.updateSession(session);
        } catch (Exception ignored) {
        }
    }

    private <T> T provider(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }

    private boolean enabled(Boolean value) {
        return value == null || value;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().map(value -> firstText(value)).filter(StringUtils::hasText).toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
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

    private String limit(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    @FunctionalInterface
    private interface ToolRuntime {
        AcademicToolStructuredOutput call(AcademicToolCallCommand command);
    }

    private record ScheduledTool(String toolName, Map<String, Object> arguments, ToolRuntime runtime) {
    }

    private record IndexedToolResult(int index, AcademicWorkspaceMragRunResponse.ToolResult result) {
    }
}
















