package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AgentWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AgentWorkspaceDataRunRequest;
import com.linrun.api.dto.AgentWorkspaceDataRunResponse;
import com.linrun.domain.agent.adapter.AgentRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.ledger.service.AgentLedgerContext;
import com.linrun.domain.agent.runtime.security.PromptInjectionGuard;
import com.linrun.domain.agent.model.AgentSession;
import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.common.AgentDataAnalysisToolRuntime;
import com.linrun.domain.agent.runtime.tool.common.AgentNl2SqlToolRuntime;
import com.linrun.domain.agent.runtime.tool.common.AgentTableRagToolRuntime;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputProjector;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentDataAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentWorkspaceDataService {

    private static final String TASK_TYPE = "workspace-data";
    private static final String ACTION = "workspace/data/run";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AgentDataAnalysisPort> dataAnalysisPort;
    private final ObjectProvider<AgentTableRagPort> tableRagPort;
    private final ObjectProvider<AgentNl2SqlPort> nl2SqlPort;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AgentRepository agentRepository;
    private final AgentExecutionLedgerService ledgerService;

    public AgentWorkspaceDataService(ObjectMapper objectMapper,
                                        ObjectProvider<AgentDataAnalysisPort> dataAnalysisPort,
                                        ObjectProvider<AgentTableRagPort> tableRagPort,
                                        ObjectProvider<AgentNl2SqlPort> nl2SqlPort,
                                        UserAccountService userAccountService,
                                        UserQuotaService userQuotaService,
                                        AgentRepository agentRepository,
                                        AgentExecutionLedgerService ledgerService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.dataAnalysisPort = dataAnalysisPort;
        this.tableRagPort = tableRagPort;
        this.nl2SqlPort = nl2SqlPort;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.agentRepository = agentRepository;
        this.ledgerService = ledgerService;
    }

    public AgentWorkspaceDataRunResponse run(String token, AgentWorkspaceDataRunRequest request) {
        AgentWorkspaceDataRunRequest safeRequest = request == null
                ? new AgentWorkspaceDataRunRequest()
                : request;
        String question = PromptInjectionGuard.sanitize(firstText(safeRequest.getQuestion(), "数据工作区分析"));
        UserAccount user = userAccountService.requireUserByToken(token);
        String userId = user.getUserId();
        preCheckQuota(userId);
        String sessionId = firstText(safeRequest.getSessionId(), "DATA" + System.currentTimeMillis());
        String requestId = "DATAREQ" + UUID.randomUUID().toString().replace("-", "");
        saveSession(userId, sessionId, question);

        AgentRun run = ledgerService.startRun(
                userId, sessionId, "", requestId, TASK_TYPE, question, "workspace-data-tools");
        AgentLedgerContext.Context context = new AgentLedgerContext.Context(
                run.getRunId(), requestId, sessionId, userId, TASK_TYPE);
        long startedAt = System.currentTimeMillis();
        List<AgentWorkspaceDataRunResponse.ToolResult> results = new ArrayList<>();
        List<String> missingTools = new ArrayList<>();
        try {
            AgentTableRagPort tablePort = provider(tableRagPort);
            if (enabled(safeRequest.getIncludeTableRag()) && tablePort != null) {
                results.add(callTool(context, AgentToolOutputNames.TABLE_RAG, tableRagArguments(requestId, safeRequest, question),
                        new AgentTableRagToolRuntime(tablePort)::call));
            } else if (enabled(safeRequest.getIncludeTableRag())) {
                missingTools.add(AgentToolOutputNames.TABLE_RAG);
            }

            AgentNl2SqlPort sqlPort = provider(nl2SqlPort);
            if (enabled(safeRequest.getIncludeNl2Sql()) && sqlPort != null) {
                results.add(callTool(context, AgentToolOutputNames.NL2SQL, nl2SqlArguments(requestId, safeRequest, question, results),
                        new AgentNl2SqlToolRuntime(sqlPort)::call));
            } else if (enabled(safeRequest.getIncludeNl2Sql())) {
                missingTools.add(AgentToolOutputNames.NL2SQL);
            }

            if (enabled(safeRequest.getIncludeAnalysis()) || results.isEmpty()) {
                AgentDataAnalysisPort analysisPort = provider(dataAnalysisPort);
                results.add(callTool(context, AgentToolOutputNames.DATA_ANALYSIS, analysisArguments(safeRequest, question),
                        new AgentDataAnalysisToolRuntime(analysisPort)::call));
            }

            String summary = summary(results, missingTools);
            long durationMillis = elapsed(startedAt);
            consumeQuota(userId, sessionId, requestId, durationMillis);
            ledgerService.finishRun(run, AgentRun.STATUS_SUCCESS, summary, "", "", durationMillis);
            updateSession(userId, sessionId, question, summary);
            return response(requestId, sessionId, run.getRunId(), summary, results, missingTools, safeRequest);
        } catch (AppException e) {
            ledgerService.finishRun(run, AgentRun.STATUS_FAILED, "", e.getCode(), e.getMessage(), elapsed(startedAt));
            throw e;
        } catch (Exception e) {
            ledgerService.finishRun(run, AgentRun.STATUS_FAILED, "", "DATA_WORKSPACE_0001",
                    firstText(e.getMessage(), "数据工作区执行失败"), elapsed(startedAt));
            throw new AppException("DATA_WORKSPACE_0001", "数据工作区执行失败：" + firstText(e.getMessage(), "未知错误"));
        }
    }

    private void preCheckQuota(String userId) {
        userQuotaService.assertEnoughQuota(userId, userQuotaService.estimatePreCheckCost(TASK_TYPE));
    }

    private void consumeQuota(String userId, String sessionId, String requestId, long durationMillis) {
        userQuotaService.consumeForAgentTask(userId, sessionId, TASK_TYPE + "-" + requestId, TASK_TYPE,
                TokenUsageMetrics.empty(), TASK_TYPE + "-tools", durationMillis);
    }

    public AgentWorkspaceDataHistoryResponse history(String token, String sessionId, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<AgentRun> runs = new ArrayList<>();
        if (StringUtils.hasText(sessionId)) {
            runs.addAll(ledgerService.queryRuns(user.getUserId(), sessionId.trim(), safeLimit));
        } else {
            for (AgentSession session : agentRepository.querySessions(user.getUserId(), safeLimit)) {
                runs.addAll(ledgerService.queryRuns(user.getUserId(), session.getSessionId(), safeLimit));
                if (runs.size() >= safeLimit) {
                    break;
                }
            }
        }
        List<AgentWorkspaceDataHistoryResponse.Item> items = runs.stream()
                .filter(run -> TASK_TYPE.equals(run.getTaskType()))
                .limit(safeLimit)
                .map(this::historyItem)
                .toList();
        AgentWorkspaceDataHistoryResponse response = new AgentWorkspaceDataHistoryResponse();
        response.setSessionId(firstText(sessionId));
        response.setTotal(items.size());
        response.setItems(items);
        return response;
    }

    public AgentWorkspaceDataCatalogResponse catalog(String token) {
        userAccountService.requireUserByToken(token);
        AgentWorkspaceDataCatalogResponse response = new AgentWorkspaceDataCatalogResponse();
        response.setDefaultModelCodeList(List.of(
                "paper_metadata",
                "experiment_result",
                "citation_network",
                "reading_note"));
        response.setModels(List.of(
                model("paper_metadata", "论文元数据", "paper_metadata", "论文题名、作者、年份、关键词和摘要信息",
                        List.of("paper_id", "title", "authors", "publish_year", "venue", "keywords", "abstract_text"),
                        column("paper_id", "varchar", "论文编号", false),
                        column("title", "varchar", "论文标题", false),
                        column("authors", "varchar", "作者列表", false),
                        column("publish_year", "int", "发表年份", true),
                        column("venue", "varchar", "期刊或会议", false),
                        column("keywords", "varchar", "关键词", false),
                        column("abstract_text", "text", "摘要内容", false)),
                model("experiment_result", "实验结果", "experiment_result", "实验组、指标、数值和复现实验备注",
                        List.of("experiment_id", "paper_id", "dataset", "method_name", "metric_name", "metric_value", "run_time"),
                        column("experiment_id", "varchar", "实验编号", false),
                        column("paper_id", "varchar", "关联论文编号", false),
                        column("dataset", "varchar", "数据集名称", false),
                        column("method_name", "varchar", "方法名称", false),
                        column("metric_name", "varchar", "评价指标", false),
                        column("metric_value", "decimal", "指标数值", true),
                        column("run_time", "datetime", "实验运行时间", false)),
                model("citation_network", "引用网络", "citation_network", "论文之间的引用、被引和关系类型",
                        List.of("source_paper_id", "target_paper_id", "relation_type", "citation_context"),
                        column("source_paper_id", "varchar", "引用方论文编号", false),
                        column("target_paper_id", "varchar", "被引用论文编号", false),
                        column("relation_type", "varchar", "引用关系类型", false),
                        column("citation_context", "text", "引用上下文", false)),
                model("reading_note", "阅读笔记", "reading_note", "用户整理的论文笔记、问题和结论",
                        List.of("note_id", "paper_id", "section_name", "note_text", "tag", "create_time"),
                        column("note_id", "varchar", "笔记编号", false),
                        column("paper_id", "varchar", "关联论文编号", false),
                        column("section_name", "varchar", "章节或主题", false),
                        column("note_text", "text", "笔记内容", false),
                        column("tag", "varchar", "笔记标签", false),
                        column("create_time", "datetime", "创建时间", false))));
        response.setSampleQuestions(List.of(
                "统计近五年 RAG 相关论文的发表趋势",
                "比较不同方法在同一数据集上的实验指标",
                "找出某篇论文引用链路中的核心相关工作"));
        return response;
    }
    private AgentWorkspaceDataRunResponse.ToolResult callTool(AgentLedgerContext.Context context,
                                                                 String toolName,
                                                                 Map<String, Object> arguments,
                                                                 ToolRuntime runtime) {
        String invocationId = ledgerService.recordToolStart(
                context,
                "workspace-data-" + toolName + "-" + context.requestId(),
                toolName,
                ACTION,
                json(arguments));
        long startedAt = System.currentTimeMillis();
        try {
            AgentToolStructuredOutput output = runtime.call(AgentToolCallCommand.builder(toolName)
                    .action(ACTION)
                    .requestId(context.requestId())
                    .sessionId(context.sessionId())
                    .userId(context.userId())
                    .runId(context.runId())
                    .arguments(arguments)
                    .build());
            Map<String, Object> result = AgentToolOutputProjector.toResultMap(output);
            ledgerService.recordToolFinish(invocationId, AgentRun.STATUS_SUCCESS,
                    AgentToolOutputProjector.summarize(output), json(result), 0, "", elapsed(startedAt));
            if (!output.getFileRefs().isEmpty()) {
                ledgerService.recordToolArtifacts(context, invocationId, output.getToolName(), result);
            }
            return toolResult(invocationId, output, result);
        } catch (Exception e) {
            ledgerService.recordToolFinish(invocationId, AgentRun.STATUS_FAILED,
                    "", "{}", 0, firstText(e.getMessage(), "tool failed"), elapsed(startedAt));
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AppException("DATA_WORKSPACE_TOOL_FAILED", firstText(e.getMessage(), "tool failed"));
        }
    }

    private Map<String, Object> tableRagArguments(String requestId,
                                                  AgentWorkspaceDataRunRequest request,
                                                  String question) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("requestId", requestId);
        arguments.put("query", question);
        arguments.put("modelCodeList", safeList(request.getModelCodeList()));
        arguments.put("recallType", "only_recall");
        arguments.put("useVector", enabled(request.getUseVector()));
        arguments.put("useElastic", enabled(request.getUseElastic()));
        arguments.put("topK", Math.max(1, request.getTopK() == null ? 5 : request.getTopK()));
        return arguments;
    }

    private Map<String, Object> nl2SqlArguments(String requestId,
                                                AgentWorkspaceDataRunRequest request,
                                                String question,
                                                List<AgentWorkspaceDataRunResponse.ToolResult> results) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("requestId", requestId);
        arguments.put("query", question);
        arguments.put("modelCodeList", safeList(request.getModelCodeList()));
        arguments.put("schemaInfo", schemaInfo(request, results));
        arguments.put("currentDateInfo", "current date: " + LocalDate.now());
        arguments.put("dbType", firstText(request.getDbType(), "mysql"));
        arguments.put("stream", false);
        arguments.put("useVector", enabled(request.getUseVector()));
        arguments.put("useElastic", enabled(request.getUseElastic()));
        return arguments;
    }

    private Map<String, Object> analysisArguments(AgentWorkspaceDataRunRequest request, String question) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("task", question);
        arguments.put("rows", request.getRows() == null ? List.of() : request.getRows());
        arguments.put("columns", safeList(request.getColumns()));
        arguments.put("modelCodeList", safeList(request.getModelCodeList()));
        arguments.put("businessKnowledge", firstText(request.getBusinessKnowledge()));
        arguments.put("maxSteps", Math.max(1, request.getMaxSteps() == null ? 10 : request.getMaxSteps()));
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> schemaInfo(AgentWorkspaceDataRunRequest request,
                                                 List<AgentWorkspaceDataRunResponse.ToolResult> results) {
        if (request.getSchemaInfo() != null && !request.getSchemaInfo().isEmpty()) {
            return request.getSchemaInfo();
        }
        for (AgentWorkspaceDataRunResponse.ToolResult result : results) {
            if (!AgentToolOutputNames.TABLE_RAG.equals(result.getToolName())) {
                continue;
            }
            Object metadata = result.getStructuredOutput().get("metadata");
            if (metadata instanceof Map<?, ?> map && map.get("matches") instanceof List<?> matches) {
                return matches.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) item))
                        .toList();
            }
        }
        return List.of();
    }

    private AgentWorkspaceDataRunResponse.ToolResult toolResult(String invocationId,
                                                                   AgentToolStructuredOutput output,
                                                                   Map<String, Object> resultMap) {
        AgentWorkspaceDataRunResponse.ToolResult result = new AgentWorkspaceDataRunResponse.ToolResult();
        result.setInvocationId(invocationId);
        result.setToolName(output.getToolName());
        result.setTitle(output.getTitle());
        result.setSummary(output.getSummary());
        result.setContent(output.getContent());
        result.setStructuredOutput(resultMap);
        result.setFileRefs(fileRefs(output.getFileRefs()));
        return result;
    }

    private List<Map<String, Object>> fileRefs(List<AgentToolFileRef> refs) {
        return refs == null ? List.of() : refs.stream().map(AgentToolFileRef::toMap).toList();
    }

    private AgentWorkspaceDataRunResponse response(String requestId,
                                                      String sessionId,
                                                      String runId,
                                                      String summary,
                                                      List<AgentWorkspaceDataRunResponse.ToolResult> results,
                                                      List<String> missingTools,
                                                      AgentWorkspaceDataRunRequest request) {
        AgentWorkspaceDataRunResponse response = new AgentWorkspaceDataRunResponse();
        response.setRequestId(requestId);
        response.setSessionId(sessionId);
        response.setRunId(runId);
        response.setSummary(summary);
        response.setToolResults(results);
        response.setMissingTools(missingTools);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelCodeList", safeList(request.getModelCodeList()));
        metadata.put("rowCount", request.getRows() == null ? 0 : request.getRows().size());
        metadata.put("columnCount", request.getColumns() == null ? 0 : request.getColumns().size());
        metadata.put("executedToolCount", results.size());
        response.setMetadata(metadata);
        return response;
    }

    private String summary(List<AgentWorkspaceDataRunResponse.ToolResult> results, List<String> missingTools) {
        List<String> parts = results.stream()
                .map(item -> firstText(item.getSummary(), item.getTitle(), item.getToolName()))
                .filter(StringUtils::hasText)
                .toList();
        String summary = parts.isEmpty() ? "数据工作区已完成工具运行" : String.join("；", parts);
        if (!missingTools.isEmpty()) {
            summary = summary + "；未配置工具: " + String.join(", ", missingTools);
        }
        return summary;
    }

    private AgentWorkspaceDataHistoryResponse.Item historyItem(AgentRun run) {
        AgentWorkspaceDataHistoryResponse.Item item = new AgentWorkspaceDataHistoryResponse.Item();
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

    private AgentWorkspaceDataCatalogResponse.Model model(String modelCode,
                                                             String displayName,
                                                             String tableName,
                                                             String description,
                                                             List<String> defaultRecallFields,
                                                             AgentWorkspaceDataCatalogResponse.Column... columns) {
        AgentWorkspaceDataCatalogResponse.Model model = new AgentWorkspaceDataCatalogResponse.Model();
        model.setModelCode(modelCode);
        model.setDisplayName(displayName);
        model.setTableName(tableName);
        model.setDescription(description);
        model.setDefaultRecallFields(defaultRecallFields == null ? List.of() : defaultRecallFields);
        model.setColumns(List.of(columns));
        return model;
    }

    private AgentWorkspaceDataCatalogResponse.Column column(String name,
                                                               String type,
                                                               String description,
                                                               boolean metric) {
        AgentWorkspaceDataCatalogResponse.Column column = new AgentWorkspaceDataCatalogResponse.Column();
        column.setName(name);
        column.setType(type);
        column.setDescription(description);
        column.setMetric(metric);
        return column;
    }

    private void saveSession(String userId, String sessionId, String question) {
        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(question, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(limit(question, 240));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(session.getCreateTime());
        try {
            agentRepository.saveSessionIfAbsent(session);
        } catch (Exception ignored) {
        }
    }

    private void updateSession(String userId, String sessionId, String question, String summary) {
        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(question, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(firstText(limit(summary, 240), limit(question, 240)));
        session.setUpdateTime(LocalDateTime.now());
        try {
            agentRepository.updateSession(session);
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

    private String metadataText(AgentWorkspaceDataRunRequest request, String key) {
        if (request == null || request.getMetadata() == null || !request.getMetadata().containsKey(key)) {
            return "";
        }
        return firstText(request.getMetadata().get(key));
    }

    private String limit(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    @FunctionalInterface
    private interface ToolRuntime {
        AgentToolStructuredOutput call(AgentToolCallCommand command);
    }
}
















