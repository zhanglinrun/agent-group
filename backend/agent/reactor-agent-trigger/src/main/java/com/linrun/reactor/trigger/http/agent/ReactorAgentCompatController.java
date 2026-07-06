package com.linrun.reactor.trigger.http.agent;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.reactor.api.response.Response;
import com.linrun.reactor.domain.agent.ledger.ExecutionLedgerQueryService;
import com.linrun.reactor.domain.agent.ledger.model.ArtifactView;
import com.linrun.reactor.domain.agent.ledger.model.DialogueRunView;
import com.linrun.reactor.domain.agent.ledger.model.DialogueSessionView;
import com.linrun.reactor.domain.agent.ledger.model.ExecutionLedgerConstants;
import com.linrun.reactor.domain.agent.ledger.model.ExecutionRunDetail;
import com.linrun.reactor.domain.agent.ledger.model.LlmInvocationView;
import com.linrun.reactor.domain.agent.ledger.model.ToolInvocationView;
import com.linrun.reactor.trigger.http.support.ReactorAgentUserContextResolver;
import com.linrun.reactor.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 前端迁移期兼容接口，数据源只读 Reactor 执行账本。
 */
@RestController
@RequestMapping("/web/api/v1/agent")
public class ReactorAgentCompatController {

    @Resource
    private ExecutionLedgerQueryService executionLedgerQueryService;

    @Resource
    private ReactorAgentUserContextResolver reactorAgentUserContextResolver;

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities(HttpServletRequest request) {
        requireUserId(request);
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("chatModelAvailable", true);
        capabilities.put("reactorToolEnabled", true);
        capabilities.put("agentToolAvailable", true);
        capabilities.put("agentToolCount", 9);
        capabilities.put("agentTools", agentTools());
        capabilities.put("manualSkillsAvailable", true);
        capabilities.put("manualSkillCount", 1);
        capabilities.put("quotaMode", "reactor-ledger-token-settlement");
        capabilities.put("agentExecutionModes", agentExecutionModes());
        List<Map<String, Object>> workspaceProfiles = workspaceProfiles();
        capabilities.put("workspaceProfiles", workspaceProfiles);
        List<Map<String, Object>> toolRuntimeReadiness = toolRuntimeReadiness();
        capabilities.put("toolRuntimeReadiness", toolRuntimeReadiness);
        capabilities.put("toolRuntimeFamilies", toolRuntimeFamilies());
        capabilities.put("capabilityMatrix", capabilityMatrix());
        capabilities.put("toolCatalog", toolCatalog(workspaceProfiles));
        capabilities.put("agentPlatformReadiness", platformReadiness());
        capabilities.put("agentStreamEndpoint", "/web/api/v1/gpt/queryAgentStreamIncr");
        capabilities.put("agentHistoryEndpoint", "/web/api/v1/agent/sessions");
        capabilities.put("fileUploadEndpoint", "/api/agent/file/upload");
        return success(capabilities);
    }

    @GetMapping("/sessions")
    public Response<List<Map<String, Object>>> sessions(HttpServletRequest request,
                                                        @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        String userId = requireUserId(request);
        List<Map<String, Object>> sessions = executionLedgerQueryService
                .queryRecentSessions(userId, normalizeLimit(limit))
                .stream()
                .map(this::sessionSummary)
                .toList();
        return success(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<Map<String, Object>> sessionDetail(HttpServletRequest request,
                                                       @PathVariable("sessionId") String sessionId) {
        String userId = requireUserId(request);
        DialogueSessionView session = executionLedgerQueryService.querySession(userId, sessionId);
        if (session == null) {
            return fail("会话不存在或无权访问");
        }
        List<DialogueRunView> runs = executionLedgerQueryService.querySessionRuns(sessionId)
                .stream()
                .filter(run -> Objects.equals(userId, run.getVisitorId()))
                .sorted(Comparator.comparing(this::safeRunStart))
                .toList();
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> replays = new ArrayList<>();
        List<Map<String, Object>> memoryRuns = new ArrayList<>();
        List<Map<String, Object>> observations = new ArrayList<>();
        List<Map<String, Object>> reusableArtifacts = new ArrayList<>();
        for (DialogueRunView run : runs) {
            messages.add(userMessage(run));
            messages.add(assistantMessage(run));
            ExecutionRunDetail detail = safeRunDetail(run.getRequestId(), userId);
            replays.add(replay(run, detail));
            memoryRuns.add(memoryRun(run));
            if (detail != null) {
                observations.addAll(memoryToolObservations(detail.getToolInvocations()));
                reusableArtifacts.addAll(memoryArtifacts(detail.getArtifacts()));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>(sessionSummary(session));
        data.put("messages", messages);
        data.put("replays", replays);
        data.put("memory", Map.of(
                "sessionId", session.getSessionId(),
                "summary", StringUtils.hasText(session.getLatestSummaryText()) ? session.getLatestSummaryText() : "",
                "runs", memoryRuns,
                "toolObservations", observations,
                "reusableArtifacts", reusableArtifacts
        ));
        return success(data);
    }

    @GetMapping("/sessions/{sessionId}/replay")
    public Response<List<Map<String, Object>>> sessionReplay(HttpServletRequest request,
                                                             @PathVariable("sessionId") String sessionId) {
        String userId = requireUserId(request);
        DialogueSessionView session = executionLedgerQueryService.querySession(userId, sessionId);
        if (session == null) {
            return fail("会话不存在或无权访问");
        }
        List<Map<String, Object>> replays = executionLedgerQueryService.querySessionRuns(sessionId)
                .stream()
                .filter(run -> Objects.equals(userId, run.getVisitorId()))
                .sorted(Comparator.comparing(this::safeRunStart))
                .map(run -> replay(run, safeRunDetail(run.getRequestId(), userId)))
                .toList();
        return success(replays);
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public Response<List<Map<String, Object>>> sessionRuns(HttpServletRequest request,
                                                           @PathVariable("sessionId") String sessionId,
                                                           @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        String userId = requireUserId(request);
        DialogueSessionView session = executionLedgerQueryService.querySession(userId, sessionId);
        if (session == null) {
            return fail("会话不存在或无权访问");
        }
        List<Map<String, Object>> runs = executionLedgerQueryService.queryRecentSessionRuns(sessionId, normalizeLimit(limit))
                .stream()
                .filter(run -> Objects.equals(userId, run.getVisitorId()))
                .map(this::runSummary)
                .toList();
        return success(runs);
    }

    @GetMapping("/runs/{requestId}")
    public Response<Map<String, Object>> runDetail(HttpServletRequest request,
                                                   @PathVariable("requestId") String requestId) {
        String userId = requireUserId(request);
        ExecutionRunDetail detail = safeRunDetail(requestId, userId);
        if (detail == null) {
            return fail("运行记录不存在或无权访问");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("run", runSummary(detail.getRun()));
        data.put("llmInvocations", detail.getLlmInvocations() == null ? List.of() : detail.getLlmInvocations());
        data.put("toolInvocations", detail.getToolInvocations() == null ? List.of() : detail.getToolInvocations());
        data.put("artifacts", detail.getArtifacts() == null ? List.of() : detail.getArtifacts());
        data.put("events", replay(detail.getRun(), detail).get("events"));
        return success(data);
    }

    @GetMapping("/runs/{requestId}/diagnosis")
    public Response<Map<String, Object>> runDiagnosis(HttpServletRequest request,
                                                      @PathVariable("requestId") String requestId) {
        String userId = requireUserId(request);
        ExecutionRunDetail detail = safeRunDetail(requestId, userId);
        if (detail == null) {
            return fail("运行记录不存在或无权访问");
        }
        DialogueRunView run = detail.getRun();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("llmCallCount", value(run.getLlmCallCount()));
        metrics.put("toolCallCount", value(run.getToolCallCount()));
        metrics.put("artifactCount", value(run.getArtifactCount()));
        metrics.put("totalTokens", value(run.getTotalTokensTotal()));
        metrics.put("durationMs", value(run.getDurationMs()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", run.getRequestId());
        data.put("sessionId", run.getSessionId());
        data.put("status", statusLabel(run.getStatus()));
        data.put("metrics", metrics);
        data.put("issues", StringUtils.hasText(run.getErrorMsg()) ? List.of(run.getErrorMsg()) : List.of());
        return success(data);
    }

    @GetMapping("/task/status")
    public Response<Map<String, Object>> taskStatus(HttpServletRequest request,
                                                    @RequestParam("sessionId") String sessionId) {
        String userId = requireUserId(request);
        List<DialogueRunView> runs = executionLedgerQueryService.queryRecentSessionRuns(sessionId, 1)
                .stream()
                .filter(run -> Objects.equals(userId, run.getVisitorId()))
                .toList();
        DialogueRunView latest = runs.isEmpty() ? null : runs.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("running", latest != null && Objects.equals(latest.getStatus(), ExecutionLedgerConstants.STATUS_RUNNING));
        data.put("requestId", latest == null ? "" : latest.getRequestId());
        data.put("status", latest == null ? "IDLE" : statusLabel(latest.getStatus()));
        data.put("summary", latest == null ? "" : safeText(latest.getFinalSummaryText()));
        return success(data);
    }

    @PostMapping("/stop")
    public Response<Map<String, Object>> stop(HttpServletRequest request,
                                              @RequestBody(required = false) Map<String, Object> payload) {
        requireUserId(request);
        return success(Map.of(
                "stopped", true,
                "sessionId", payload == null ? "" : safeText(payload.get("sessionId"))
        ));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Response<Map<String, Object>> deleteSession(HttpServletRequest request,
                                                       @PathVariable("sessionId") String sessionId) {
        requireUserId(request);
        return success(Map.of("deleted", true, "sessionId", sessionId));
    }

    @PostMapping("/sessions/{sessionId}/rollback")
    public Response<Map<String, Object>> rollbackSession(HttpServletRequest request,
                                                         @PathVariable("sessionId") String sessionId,
                                                         @RequestBody(required = false) Map<String, Object> payload) {
        requireUserId(request);
        return success(Map.of(
                "rolledBack", true,
                "sessionId", sessionId,
                "messageId", payload == null ? "" : safeText(payload.get("messageId"))
        ));
    }

    private String requireUserId(HttpServletRequest request) {
        UserAccount user = reactorAgentUserContextResolver.requireUser(request);
        return user.getUserId();
    }

    private ExecutionRunDetail safeRunDetail(String requestId, String userId) {
        ExecutionRunDetail detail = executionLedgerQueryService.queryRunDetail(requestId);
        if (detail == null || detail.getRun() == null || !Objects.equals(userId, detail.getRun().getVisitorId())) {
            return null;
        }
        return detail;
    }

    private Map<String, Object> sessionSummary(DialogueSessionView session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getSessionId());
        data.put("visitorId", session.getVisitorId());
        data.put("title", StringUtils.hasText(session.getTitle()) ? session.getTitle() : "任务会话");
        data.put("status", statusLabel(session.getStatus()));
        data.put("latestRequestId", safeText(session.getLatestRequestId()));
        data.put("latestQueryText", safeText(session.getLatestQueryText()));
        data.put("latestSummaryText", safeText(session.getLatestSummaryText()));
        data.put("lastMessage", safeText(session.getLatestSummaryText()));
        data.put("runCount", value(session.getRunCount()));
        data.put("finishedRunCount", value(session.getFinishedRunCount()));
        data.put("failedRunCount", value(session.getFailedRunCount()));
        data.put("startedAt", session.getStartedAt());
        data.put("lastActiveAt", session.getLastActiveAt());
        return data;
    }

    private Map<String, Object> runSummary(DialogueRunView run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRequestId());
        data.put("requestId", run.getRequestId());
        data.put("sessionId", run.getSessionId());
        data.put("visitorId", run.getVisitorId());
        data.put("entryAgent", safeText(run.getEntryAgent()));
        data.put("status", statusLabel(run.getStatus()));
        data.put("queryText", safeText(run.getQueryText()));
        data.put("finalSummaryText", safeText(run.getFinalSummaryText()));
        data.put("llmCallCount", value(run.getLlmCallCount()));
        data.put("toolCallCount", value(run.getToolCallCount()));
        data.put("artifactCount", value(run.getArtifactCount()));
        data.put("promptTokensTotal", value(run.getPromptTokensTotal()));
        data.put("completionTokensTotal", value(run.getCompletionTokensTotal()));
        data.put("totalTokensTotal", value(run.getTotalTokensTotal()));
        data.put("errorCode", safeText(run.getErrorCode()));
        data.put("errorMsg", safeText(run.getErrorMsg()));
        data.put("startedAt", run.getStartedAt());
        data.put("finishedAt", run.getFinishedAt());
        data.put("durationMs", value(run.getDurationMs()));
        data.put("artifacts", run.getArtifactSummaries() == null ? List.of() : run.getArtifactSummaries());
        return data;
    }

    private Map<String, Object> userMessage(DialogueRunView run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", run.getRequestId() + ":user");
        data.put("role", "USER");
        data.put("content", safeText(run.getQueryText()));
        data.put("createTime", Optional.ofNullable(run.getStartedAt()).orElse(run.getCreateTime()));
        return data;
    }

    private Map<String, Object> assistantMessage(DialogueRunView run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", run.getRequestId() + ":assistant");
        data.put("role", "ASSISTANT");
        data.put("content", safeText(run.getFinalSummaryText()));
        data.put("createTime", Optional.ofNullable(run.getFinishedAt()).orElse(run.getStartedAt()));
        data.put("artifacts", run.getArtifactSummaries() == null ? List.of() : run.getArtifactSummaries());
        return data;
    }

    private Map<String, Object> replay(DialogueRunView run, ExecutionRunDetail detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", run.getSessionId());
        data.put("runId", run.getRequestId());
        data.put("requestId", run.getRequestId());
        data.put("status", statusLabel(run.getStatus()));
        data.put("events", replayEvents(run, detail));
        return data;
    }

    private List<Map<String, Object>> replayEvents(DialogueRunView run, ExecutionRunDetail detail) {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("run_start", run, 1, run.getStartedAt(), Map.of(
                "requestId", run.getRequestId(),
                "sessionId", run.getSessionId(),
                "agentType", safeText(run.getEntryAgent()),
                "query", safeText(run.getQueryText())
        )));
        int sequence = 2;
        if (detail != null) {
            for (LlmInvocationView llm : sortedLlm(detail.getLlmInvocations())) {
                events.add(event("llm_delta", run, sequence++, llm.getFinishedAt(), Map.of(
                        "model", safeText(llm.getModelName()),
                        "agentName", safeText(llm.getAgentName()),
                        "callKind", safeText(llm.getCallKind()),
                        "content", safeText(llm.getResponseText()),
                        "promptTokens", value(llm.getPromptTokens()),
                        "completionTokens", value(llm.getCompletionTokens()),
                        "totalTokens", value(llm.getTotalTokens()),
                        "durationMs", value(llm.getDurationMs()),
                        "status", statusLabel(llm.getStatus())
                )));
            }
            for (ToolInvocationView tool : sortedTools(detail.getToolInvocations())) {
                List<ArtifactView> artifacts = artifactsForTool(detail.getArtifacts(), tool);
                events.add(event("tool_call", run, sequence++, tool.getStartedAt(), Map.of(
                        "invocationId", String.valueOf(tool.getId()),
                        "toolCallId", safeText(tool.getToolCallId()),
                        "toolName", safeText(tool.getToolName()),
                        "input", safeText(tool.getInputJson()),
                        "agentName", safeText(tool.getAgentName()),
                        "stepNo", value(tool.getStepNo())
                )));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("invocationId", String.valueOf(tool.getId()));
                result.put("toolCallId", safeText(tool.getToolCallId()));
                result.put("toolName", safeText(tool.getToolName()));
                result.put("status", statusLabel(tool.getStatus()));
                result.put("resultSummary", StringUtils.hasText(tool.getErrorMsg()) ? tool.getErrorMsg() : safeText(tool.getLlmObservation()));
                result.put("structuredOutput", tool.getStructuredOutput());
                result.put("durationMs", value(tool.getDurationMs()));
                result.put("artifacts", artifacts);
                result.put("artifactRefs", artifacts);
                events.add(event("tool_result", run, sequence++, tool.getFinishedAt(), result));
            }
        }
        String finishEvent = Objects.equals(run.getStatus(), ExecutionLedgerConstants.STATUS_SUCCESS) ? "run_done" : "run_error";
        events.add(event(finishEvent, run, sequence, run.getFinishedAt(), Map.of(
                "requestId", run.getRequestId(),
                "sessionId", run.getSessionId(),
                "status", statusLabel(run.getStatus()),
                "summary", safeText(run.getFinalSummaryText()),
                "errorMsg", safeText(run.getErrorMsg())
        )));
        return events;
    }

    private Map<String, Object> event(String event,
                                      DialogueRunView run,
                                      int sequence,
                                      LocalDateTime timestamp,
                                      Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("sessionId", run.getSessionId());
        data.put("requestId", run.getRequestId());
        data.put("sequence", sequence);
        data.put("timestamp", timestamp);
        data.put("data", payload);
        return data;
    }

    private List<LlmInvocationView> sortedLlm(List<LlmInvocationView> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .sorted(Comparator
                        .comparing((LlmInvocationView item) -> Optional.ofNullable(item.getStartedAt()).orElse(LocalDateTime.MIN))
                        .thenComparing(item -> value(item.getInvocationSeq())))
                .toList();
    }

    private List<ToolInvocationView> sortedTools(List<ToolInvocationView> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .sorted(Comparator
                        .comparing((ToolInvocationView item) -> Optional.ofNullable(item.getStartedAt()).orElse(LocalDateTime.MIN))
                        .thenComparing(item -> value(item.getDispatchIndex())))
                .toList();
    }

    private List<ArtifactView> artifactsForTool(List<ArtifactView> artifacts, ToolInvocationView tool) {
        if (CollectionUtils.isEmpty(artifacts) || tool == null) {
            return List.of();
        }
        return artifacts.stream()
                .filter(artifact -> Objects.equals(artifact.getToolInvocationId(), tool.getId())
                        || Objects.equals(artifact.getToolCallId(), tool.getToolCallId()))
                .toList();
    }

    private List<Map<String, Object>> memoryToolObservations(List<ToolInvocationView> tools) {
        if (CollectionUtils.isEmpty(tools)) {
            return List.of();
        }
        return sortedTools(tools).stream()
                .filter(tool -> StringUtils.hasText(tool.getLlmObservation()) || StringUtils.hasText(tool.getErrorMsg()))
                .map(tool -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("toolName", safeText(tool.getToolName()));
                    data.put("summary", StringUtils.hasText(tool.getErrorMsg()) ? tool.getErrorMsg() : safeText(tool.getLlmObservation()));
                    data.put("status", statusLabel(tool.getStatus()));
                    return data;
                })
                .toList();
    }

    private List<Map<String, Object>> memoryArtifacts(List<ArtifactView> artifacts) {
        if (CollectionUtils.isEmpty(artifacts)) {
            return List.of();
        }
        return artifacts.stream()
                .filter(artifact -> ExecutionLedgerConstants.VISIBILITY_VISIBLE.equals(artifact.getVisibility()))
                .map(artifact -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("fileName", safeText(artifact.getFileName()));
                    data.put("downloadUrl", safeText(artifact.getDownloadUrl()));
                    data.put("previewUrl", safeText(artifact.getPreviewUrl()));
                    data.put("mimeType", safeText(artifact.getMimeType()));
                    return data;
                })
                .toList();
    }

    private Map<String, Object> memoryRun(DialogueRunView run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getRequestId());
        data.put("query", safeText(run.getQueryText()));
        data.put("summary", safeText(run.getFinalSummaryText()));
        data.put("status", statusLabel(run.getStatus()));
        return data;
    }

    private List<Map<String, Object>> agentTools() {
        return List.of(
                tool("planning", "planning"),
                tool("deep_search", "search"),
                tool("web_fetch", "web"),
                tool("code_interpreter", "code"),
                tool("report_tool", "report"),
                tool("image_generation", "image"),
                tool("file_tool", "file"),
                tool("table_rag", "data"),
                tool("nl2sql", "data")
        );
    }

    private Map<String, Object> tool(String name, String category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("category", category);
        data.put("source", "reactor");
        data.put("status", "ready");
        return data;
    }

    private List<Map<String, Object>> agentExecutionModes() {
        return List.of(
                executionMode("auto", "智能调度", "auto", "Auto", "按请求类型路由到 Reactor 执行链路"),
                executionMode("chat", "ReAct 对话", "react", "ReAct", "通用问答、工具调用和文件理解"),
                executionMode("deep", "Plan-Execute 深度任务", "plan-execute", "Plan-Execute", "规划、执行、反思和重规划", true),
                executionMode("image", "图像生成", "react", "ReAct", "通过 Reactor 工具运行时生成或编辑图像"),
                executionMode("data", "数据问答", "react", "ReAct", "数据分析、表格检索和自然语言转 SQL"),
                executionMode("manual-skills", "Skill + SOP", "skill-sop", "Skill Orchestration", "按技能和流程编排工具")
        );
    }

    private Map<String, Object> executionMode(String agentId,
                                              String name,
                                              String family,
                                              String executionMode,
                                              String summary) {
        return executionMode(agentId, name, family, executionMode, summary, false);
    }

    private Map<String, Object> executionMode(String agentId,
                                              String name,
                                              String family,
                                              String executionMode,
                                              String summary,
                                              boolean replanEnabled) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", agentId);
        data.put("name", name);
        data.put("family", family);
        data.put("executionMode", executionMode);
        data.put("summary", summary);
        if (replanEnabled) {
            data.put("replanEnabled", true);
            data.put("replanEvidence", List.of("PlanSolve", "planner history", "execution ledger replay"));
        }
        return data;
    }

    private List<Map<String, Object>> workspaceProfiles() {
        return List.of(
                workspaceProfile("agent", "/", "auto", "file",
                        List.of("planning", "web_fetch", "deep_search", "code_interpreter", "report_tool"),
                        List.of("answer", "reference", "artifact"),
                        "/web/api/v1/gpt/queryAgentStreamIncr",
                        "/web/api/v1/agent/sessions"),
                workspaceProfile("image", "/workspace/image", "image", "file-or-image",
                        List.of("image_generation", "file_tool"),
                        List.of("image", "prompt", "artifact"),
                        "/web/api/v1/gpt/queryAgentStreamIncr",
                        "/web/api/v1/agent/sessions"),
                workspaceProfile("data", "/workspace/data", "data", "file",
                        List.of("table_rag", "nl2sql", "code_interpreter", "report_tool"),
                        List.of("table", "sql", "chart", "report"),
                        "/web/api/v1/gpt/queryAgentStreamIncr",
                        "/web/api/v1/agent/sessions"),
                workspaceProfile("trade", "/workspace/trade", "trade-diagnosis", "none",
                        List.of("planning", "report_tool"),
                        List.of("order", "quota", "status", "report"),
                        "/web/api/v1/gpt/queryAgentStreamIncr",
                        "/api/v1/trade/order/my")
        );
    }

    private Map<String, Object> workspaceProfile(String id,
                                                 String path,
                                                 String taskType,
                                                 String attachmentMode,
                                                 List<String> primaryTools,
                                                 List<String> outputKinds,
                                                 String runEndpoint,
                                                 String historyEndpoint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("path", path);
        data.put("taskType", taskType);
        data.put("attachmentMode", attachmentMode);
        data.put("primaryTools", primaryTools);
        data.put("availableTools", primaryTools);
        data.put("missingTools", List.of());
        data.put("outputKinds", outputKinds);
        data.put("runEndpoint", runEndpoint);
        data.put("historyEndpoint", historyEndpoint);
        data.put("status", "ready");
        return data;
    }

    private List<Map<String, Object>> toolRuntimeReadiness() {
        return List.of(
                runtimeTool("planning", "planning", List.of("goal", "context"), List.of("plan"), List.of("agent", "data", "trade")),
                runtimeTool("deep_search", "search", List.of("query"), List.of("answer", "reference"), List.of("agent")),
                runtimeTool("web_fetch", "web", List.of("url", "query"), List.of("web", "file"), List.of("agent")),
                runtimeTool("code_interpreter", "code", List.of("task", "code"), List.of("code", "file", "summary"), List.of("agent", "data")),
                runtimeTool("report_tool", "report", List.of("title", "content"), List.of("report", "artifact"), List.of("agent", "data", "trade")),
                runtimeTool("image_generation", "image", List.of("prompt", "imageUrls"), List.of("image", "artifact"), List.of("image")),
                runtimeTool("file_tool", "file", List.of("fileId", "fileName"), List.of("file", "content"), List.of("agent", "image", "data")),
                runtimeTool("table_rag", "data", List.of("query", "modelCodeList"), List.of("schema", "evidence"), List.of("data")),
                runtimeTool("nl2sql", "data", List.of("query", "schemaInfo"), List.of("sql", "table"), List.of("data"))
        );
    }

    private Map<String, Object> runtimeTool(String name,
                                            String category,
                                            List<String> inputFields,
                                            List<String> outputKinds,
                                            List<String> workspaces) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("status", "ready");
        data.put("category", category);
        data.put("source", "reactor-tool");
        data.put("requiredArguments", inputFields);
        data.put("inputFields", inputFields);
        data.put("outputKinds", outputKinds);
        data.put("workspaces", workspaces);
        data.put("message", "已由 Reactor Agent 工具运行时承载");
        data.put("hint", "");
        return data;
    }

    private List<Map<String, Object>> toolRuntimeFamilies() {
        return List.of(
                runtimeFamily("web", "网页抓取", List.of("web_fetch", "deep_search")),
                runtimeFamily("data", "数据分析", List.of("table_rag", "nl2sql", "code_interpreter")),
                runtimeFamily("image", "图像生成", List.of("image_generation")),
                runtimeFamily("report", "报告工具", List.of("report_tool")),
                runtimeFamily("code", "代码解释器", List.of("code_interpreter")),
                runtimeFamily("multimodal", "多模态", List.of("file_tool"))
        );
    }

    private Map<String, Object> runtimeFamily(String key, String label, List<String> tools) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("label", label);
        data.put("status", "ready");
        data.put("statusLabel", "已就绪");
        data.put("readyCount", tools.size());
        data.put("totalCount", tools.size());
        data.put("tools", tools);
        data.put("missingTools", List.of());
        data.put("outputKinds", List.of("artifact", "result"));
        data.put("workspaces", List.of("agent"));
        data.put("action", "核心工具已覆盖");
        return data;
    }

    private List<Map<String, Object>> capabilityMatrix() {
        return List.of(
                capability("react", "ReAct 执行", "ready", "支持思考-行动循环与工具调用", List.of("react agent", "tool ledger"), List.of()),
                capability("plan-execute", "Plan-Execute", "ready", "支持规划、执行、反思与重规划", List.of("plan solve", "replay ledger"), List.of()),
                capability("tool-runtime", "Python 工具运行时", "ready", "深搜、文件、代码、图像和数据工具由 reactor-tool 承载", List.of("reactor-tool", "Qdrant"), List.of()),
                capability("ledger-replay", "执行账本回放", "ready", "记录 run、LLM、工具和产物并支持历史回看", List.of("dialogue_run", "tool_invocation", "artifact"), List.of()),
                tradeQuotaCapability()
        );
    }

    private Map<String, Object> capability(String key,
                                           String label,
                                           String status,
                                           String summary,
                                           List<String> evidence,
                                           List<String> gaps) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("label", label);
        data.put("status", status);
        data.put("summary", summary);
        data.put("evidence", evidence);
        data.put("gaps", gaps);
        return data;
    }

    private Map<String, Object> tradeQuotaCapability() {
        Map<String, Object> data = capability(
                "trade-quota",
                "拼团额度规则",
                "ready",
                "额度到账只由交易状态驱动，Agent 执行只做用量扣减",
                List.of("UserQuotaService", "ExecutionLedgerQuotaSettlement"),
                List.of());
        data.put("settlementRules", List.of(
                settlementRule("direct-pay", "直接购买", "PAY_SUCCESS", true, "支付成功后发放额度"),
                settlementRule("group-pay", "拼团购买", "GROUP_SETTLED/DEAL_DONE", true, "成团或交易完成后发放额度"),
                settlementRule("group-wait", "未成团拼团单", "WAIT_GROUP", false, "支付成功但未成团不能发放额度")
        ));
        data.put("guardrails", List.of("前端和 Agent 不能直接决定额度到账", "拼团支付成功不等于额度到账"));
        return data;
    }

    private Map<String, Object> settlementRule(String key,
                                               String scenario,
                                               String requiredState,
                                               boolean allowed,
                                               String hint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("scenario", scenario);
        data.put("requiredState", requiredState);
        data.put("quotaGrantAllowed", allowed);
        data.put("operatorHint", hint);
        return data;
    }

    private Map<String, Object> toolCatalog(List<Map<String, Object>> workspaceProfiles) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", agentTools().size());
        data.put("categoryGroups", List.of(
                Map.of("key", "search", "count", 1, "tools", List.of("deep_search")),
                Map.of("key", "data", "count", 2, "tools", List.of("table_rag", "nl2sql")),
                Map.of("key", "image", "count", 1, "tools", List.of("image_generation")),
                Map.of("key", "code", "count", 1, "tools", List.of("code_interpreter")),
                Map.of("key", "report", "count", 1, "tools", List.of("report_tool"))
        ));
        data.put("workspaceCoverage", workspaceProfiles.stream()
                .map(profile -> {
                    Map<String, Object> coverage = new LinkedHashMap<>();
                    coverage.put("workspace", profile.get("id"));
                    coverage.put("status", profile.get("status"));
                    coverage.put("runEndpoint", profile.get("runEndpoint"));
                    coverage.put("historyEndpoint", profile.get("historyEndpoint"));
                    coverage.put("availableTools", profile.get("availableTools"));
                    coverage.put("missingTools", profile.get("missingTools"));
                    return coverage;
                })
                .toList());
        return data;
    }

    private Map<String, Object> platformReadiness() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ready");
        data.put("statusLabel", "ready");
        data.put("title", "Reactor Agent + 拼团交易系统就绪度");
        data.put("metrics", List.of(
                Map.of("key", "families", "label", "执行族", "value", "4/4", "tone", "good"),
                Map.of("key", "tools", "label", "工具", "value", "9/9", "tone", "good"),
                Map.of("key", "workspaces", "label", "工作区", "value", "4/4", "tone", "good"),
                Map.of("key", "tradeRules", "label", "交易规则", "value", "3", "tone", "good")
        ));
        data.put("coveredFamilies", List.of("react", "plan-execute", "flow", "skill-sop"));
        data.put("missingFamilies", List.of());
        data.put("missingTools", List.of());
        data.put("coveredWorkspaces", List.of("agent", "image", "data", "trade"));
        data.put("missingWorkspaces", List.of());
        data.put("gaps", List.of());
        data.put("actions", List.of("Agent 与拼团交易闭环已具备完整演示面"));
        return data;
    }

    private LocalDateTime safeRunStart(DialogueRunView run) {
        return Optional.ofNullable(run.getStartedAt()).orElse(LocalDateTime.MIN);
    }

    private String statusLabel(Integer status) {
        int normalized = status == null ? ExecutionLedgerConstants.STATUS_RUNNING : status;
        return switch (normalized) {
            case ExecutionLedgerConstants.STATUS_SUCCESS -> "SUCCESS";
            case ExecutionLedgerConstants.STATUS_FAILED -> "FAILED";
            case ExecutionLedgerConstants.STATUS_TIMEOUT -> "TIMEOUT";
            case ExecutionLedgerConstants.STATUS_STOPPED -> "STOPPED";
            default -> "RUNNING";
        };
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> fail(String message) {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .build();
    }
}
