package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AcademicWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceDataRunRequest;
import com.linrun.api.dto.AcademicWorkspaceDataRunResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.common.AcademicDataAnalysisToolRuntime;
import com.linrun.domain.academic.runtime.tool.common.AcademicNl2SqlToolRuntime;
import com.linrun.domain.academic.runtime.tool.common.AcademicTableRagToolRuntime;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputProjector;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
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
public class AcademicWorkspaceDataService {

    private static final String TASK_TYPE = "workspace-data";
    private static final String ACTION = "workspace/data/run";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AcademicDataAnalysisPort> dataAnalysisPort;
    private final ObjectProvider<AcademicTableRagPort> tableRagPort;
    private final ObjectProvider<AcademicNl2SqlPort> nl2SqlPort;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AcademicAgentRepository academicAgentRepository;
    private final AcademicExecutionLedgerService ledgerService;

    public AcademicWorkspaceDataService(ObjectMapper objectMapper,
                                        ObjectProvider<AcademicDataAnalysisPort> dataAnalysisPort,
                                        ObjectProvider<AcademicTableRagPort> tableRagPort,
                                        ObjectProvider<AcademicNl2SqlPort> nl2SqlPort,
                                        UserAccountService userAccountService,
                                        UserQuotaService userQuotaService,
                                        AcademicAgentRepository academicAgentRepository,
                                        AcademicExecutionLedgerService ledgerService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.dataAnalysisPort = dataAnalysisPort;
        this.tableRagPort = tableRagPort;
        this.nl2SqlPort = nl2SqlPort;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.academicAgentRepository = academicAgentRepository;
        this.ledgerService = ledgerService;
    }

    public AcademicWorkspaceDataRunResponse run(String token, AcademicWorkspaceDataRunRequest request) {
        AcademicWorkspaceDataRunRequest safeRequest = request == null
                ? new AcademicWorkspaceDataRunRequest()
                : request;
        String question = firstText(safeRequest.getQuestion(), "数据工作区分析");
        UserAccount user = userAccountService.requireUserByToken(token);
        String userId = user.getUserId();
        preCheckQuota(userId);
        String sessionId = firstText(safeRequest.getSessionId(), "DATA" + System.currentTimeMillis());
        String requestId = "DATAREQ" + UUID.randomUUID().toString().replace("-", "");
        saveSession(userId, sessionId, question);

        AcademicAgentRun run = ledgerService.startRun(
                userId, sessionId, "", requestId, TASK_TYPE, question, "workspace-data-tools");
        AcademicLedgerContext.Context context = new AcademicLedgerContext.Context(
                run.getRunId(), requestId, sessionId, userId, TASK_TYPE);
        long startedAt = System.currentTimeMillis();
        List<AcademicWorkspaceDataRunResponse.ToolResult> results = new ArrayList<>();
        List<String> missingTools = new ArrayList<>();
        try {
            AcademicTableRagPort tablePort = provider(tableRagPort);
            if (enabled(safeRequest.getIncludeTableRag()) && tablePort != null) {
                results.add(callTool(context, AcademicToolOutputNames.TABLE_RAG, tableRagArguments(requestId, safeRequest, question),
                        new AcademicTableRagToolRuntime(tablePort)::call));
            } else if (enabled(safeRequest.getIncludeTableRag())) {
                missingTools.add(AcademicToolOutputNames.TABLE_RAG);
            }

            AcademicNl2SqlPort sqlPort = provider(nl2SqlPort);
            if (enabled(safeRequest.getIncludeNl2Sql()) && sqlPort != null) {
                results.add(callTool(context, AcademicToolOutputNames.NL2SQL, nl2SqlArguments(requestId, safeRequest, question, results),
                        new AcademicNl2SqlToolRuntime(sqlPort)::call));
            } else if (enabled(safeRequest.getIncludeNl2Sql())) {
                missingTools.add(AcademicToolOutputNames.NL2SQL);
            }

            if (enabled(safeRequest.getIncludeAnalysis()) || results.isEmpty()) {
                AcademicDataAnalysisPort analysisPort = provider(dataAnalysisPort);
                results.add(callTool(context, AcademicToolOutputNames.DATA_ANALYSIS, analysisArguments(safeRequest, question),
                        new AcademicDataAnalysisToolRuntime(analysisPort)::call));
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
            ledgerService.finishRun(run, AcademicAgentRun.STATUS_FAILED, "", "DATA_WORKSPACE_0001",
                    firstText(e.getMessage(), "数据工作区执行失败"), elapsed(startedAt));
            throw new AppException("DATA_WORKSPACE_0001", "数据工作区执行失败: " + firstText(e.getMessage(), "未知错误"));
        }
    }

    private void preCheckQuota(String userId) {
        userQuotaService.assertEnoughQuota(userId, userQuotaService.estimatePreCheckCost(TASK_TYPE));
    }

    private void consumeQuota(String userId, String sessionId, String requestId, long durationMillis) {
        userQuotaService.consumeForAcademicTask(userId, sessionId, TASK_TYPE + "-" + requestId, TASK_TYPE,
                GuideTokenUsage.empty(), TASK_TYPE + "-tools", durationMillis);
    }

    public AcademicWorkspaceDataHistoryResponse history(String token, String sessionId, int limit) {
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
        List<AcademicWorkspaceDataHistoryResponse.Item> items = runs.stream()
                .filter(run -> TASK_TYPE.equals(run.getTaskType()))
                .limit(safeLimit)
                .map(this::historyItem)
                .toList();
        AcademicWorkspaceDataHistoryResponse response = new AcademicWorkspaceDataHistoryResponse();
        response.setSessionId(firstText(sessionId));
        response.setTotal(items.size());
        response.setItems(items);
        return response;
    }

    public AcademicWorkspaceDataCatalogResponse catalog(String token) {
        userAccountService.requireUserByToken(token);
        AcademicWorkspaceDataCatalogResponse response = new AcademicWorkspaceDataCatalogResponse();
        response.setDefaultModelCodeList(List.of(
                "trade_order",
                "group_buy_order_lock",
                "user_quota_account",
                "user_quota_flow"));
        response.setModels(List.of(
                model("trade_order", "交易订单", "trade_order", "直接购买和拼团购买的主订单状态。",
                        List.of("order_id", "user_id", "goods_id", "buy_type", "pay_amount", "order_status", "pay_time"),
                        column("order_id", "varchar", "订单编号", false),
                        column("user_id", "varchar", "用户编号", false),
                        column("goods_id", "varchar", "商品编号", false),
                        column("buy_type", "varchar", "购买类型，direct 或 group", false),
                        column("pay_amount", "decimal", "实际支付金额", true),
                        column("order_status", "varchar", "订单状态，支付成功、已成团、已关闭等", false),
                        column("pay_time", "datetime", "支付时间", false)),
                model("group_buy_order_lock", "拼团锁单", "group_buy_order_lock", "拼团队伍、锁单和交易订单的关联状态。",
                        List.of("lock_id", "team_id", "order_id", "activity_id", "lock_status", "lock_amount", "lock_time"),
                        column("lock_id", "varchar", "锁单编号", false),
                        column("team_id", "varchar", "拼团队伍编号", false),
                        column("order_id", "varchar", "交易订单编号", false),
                        column("activity_id", "varchar", "活动编号", false),
                        column("lock_status", "varchar", "锁单状态", false),
                        column("lock_amount", "decimal", "锁单金额", true),
                        column("lock_time", "datetime", "锁单时间", false)),
                model("user_quota_account", "额度账户", "user_quota_account", "用户当前可用、冻结和已用额度。",
                        List.of("user_id", "quota_balance", "frozen_quota", "used_quota", "update_time"),
                        column("user_id", "varchar", "用户编号", false),
                        column("quota_balance", "decimal", "可用额度", true),
                        column("frozen_quota", "decimal", "冻结额度", true),
                        column("used_quota", "decimal", "已用额度", true),
                        column("update_time", "datetime", "更新时间", false)),
                model("user_quota_flow", "额度流水", "user_quota_flow", "额度发放、消耗、回滚的明细流水。",
                        List.of("flow_id", "user_id", "flow_type", "biz_id", "quota_amount", "before_balance", "after_balance"),
                        column("flow_id", "varchar", "流水编号", false),
                        column("user_id", "varchar", "用户编号", false),
                        column("flow_type", "varchar", "流水类型，发放、消耗、回滚等", false),
                        column("biz_id", "varchar", "关联业务编号", false),
                        column("quota_amount", "decimal", "额度变动值", true),
                        column("before_balance", "decimal", "变动前余额", true),
                        column("after_balance", "decimal", "变动后余额", true))));
        response.setSampleQuestions(List.of(
                "统计支付成功但尚未成团的拼团订单数量",
                "核对某个用户的额度账户余额和额度流水是否一致",
                "分析最近支付成功订单的额度发放是否符合拼团成团规则"));
        return response;
    }

    private AcademicWorkspaceDataRunResponse.ToolResult callTool(AcademicLedgerContext.Context context,
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
            throw new AppException("DATA_WORKSPACE_TOOL_FAILED", firstText(e.getMessage(), "tool failed"));
        }
    }

    private Map<String, Object> tableRagArguments(String requestId,
                                                  AcademicWorkspaceDataRunRequest request,
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
                                                AcademicWorkspaceDataRunRequest request,
                                                String question,
                                                List<AcademicWorkspaceDataRunResponse.ToolResult> results) {
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

    private Map<String, Object> analysisArguments(AcademicWorkspaceDataRunRequest request, String question) {
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
    private List<Map<String, Object>> schemaInfo(AcademicWorkspaceDataRunRequest request,
                                                 List<AcademicWorkspaceDataRunResponse.ToolResult> results) {
        if (request.getSchemaInfo() != null && !request.getSchemaInfo().isEmpty()) {
            return request.getSchemaInfo();
        }
        for (AcademicWorkspaceDataRunResponse.ToolResult result : results) {
            if (!AcademicToolOutputNames.TABLE_RAG.equals(result.getToolName())) {
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

    private AcademicWorkspaceDataRunResponse.ToolResult toolResult(String invocationId,
                                                                   AcademicToolStructuredOutput output,
                                                                   Map<String, Object> resultMap) {
        AcademicWorkspaceDataRunResponse.ToolResult result = new AcademicWorkspaceDataRunResponse.ToolResult();
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

    private AcademicWorkspaceDataRunResponse response(String requestId,
                                                      String sessionId,
                                                      String runId,
                                                      String summary,
                                                      List<AcademicWorkspaceDataRunResponse.ToolResult> results,
                                                      List<String> missingTools,
                                                      AcademicWorkspaceDataRunRequest request) {
        AcademicWorkspaceDataRunResponse response = new AcademicWorkspaceDataRunResponse();
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

    private String summary(List<AcademicWorkspaceDataRunResponse.ToolResult> results, List<String> missingTools) {
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

    private AcademicWorkspaceDataHistoryResponse.Item historyItem(AcademicAgentRun run) {
        AcademicWorkspaceDataHistoryResponse.Item item = new AcademicWorkspaceDataHistoryResponse.Item();
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

    private AcademicWorkspaceDataCatalogResponse.Model model(String modelCode,
                                                             String displayName,
                                                             String tableName,
                                                             String description,
                                                             List<String> defaultRecallFields,
                                                             AcademicWorkspaceDataCatalogResponse.Column... columns) {
        AcademicWorkspaceDataCatalogResponse.Model model = new AcademicWorkspaceDataCatalogResponse.Model();
        model.setModelCode(modelCode);
        model.setDisplayName(displayName);
        model.setTableName(tableName);
        model.setDescription(description);
        model.setDefaultRecallFields(defaultRecallFields == null ? List.of() : defaultRecallFields);
        model.setColumns(List.of(columns));
        return model;
    }

    private AcademicWorkspaceDataCatalogResponse.Column column(String name,
                                                               String type,
                                                               String description,
                                                               boolean metric) {
        AcademicWorkspaceDataCatalogResponse.Column column = new AcademicWorkspaceDataCatalogResponse.Column();
        column.setName(name);
        column.setType(type);
        column.setDescription(description);
        column.setMetric(metric);
        return column;
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

    private String metadataText(AcademicWorkspaceDataRunRequest request, String key) {
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
        AcademicToolStructuredOutput call(AcademicToolCallCommand command);
    }
}
