package com.linrun.trigger.http.agent.support;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.tool.AcademicToolCallbackFactory;
import com.linrun.trigger.http.agent.AgentAdminConfigHandler;
import com.linrun.trigger.http.agent.McpAdminHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 的能力展示服务，从 AcademicAgentNativeService 抽出。
 * 负责组装 capabilities 能力清单及其全部展示子结构：执行模式、工作区画像、
 * 工具运行时与工具族就绪度、能力矩阵、平台就绪度、MCP/Admin 健康度等。
 */
@Service
public class AcademicAgentCapabilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcademicAgentCapabilityService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AcademicWebSearchMcpClient webSearchMcpClient;
    private final AcademicToolCallbackFactory academicToolCallbackFactory;
    private final AgentAdminConfigHandler agentAdminConfigHandler;
    private final ObjectProvider<McpAdminHandler> mcpAdminHandler;
    private final SkillsRuntimeResolver skillsRuntimeResolver;

    @Value("${agent.group.reactor-tool.enabled:false}")
    private boolean reactorToolEnabled;

    public AcademicAgentCapabilityService(ObjectProvider<ChatModel> chatModelProvider,
                                          AcademicWebSearchMcpClient webSearchMcpClient,
                                          AcademicToolCallbackFactory academicToolCallbackFactory,
                                          AgentAdminConfigHandler agentAdminConfigHandler,
                                          ObjectProvider<McpAdminHandler> mcpAdminHandler,
                                          SkillsRuntimeResolver skillsRuntimeResolver) {
        this.chatModelProvider = chatModelProvider;
        this.webSearchMcpClient = webSearchMcpClient;
        this.academicToolCallbackFactory = academicToolCallbackFactory;
        this.agentAdminConfigHandler = agentAdminConfigHandler;
        this.mcpAdminHandler = mcpAdminHandler;
        this.skillsRuntimeResolver = skillsRuntimeResolver;
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        String resolvedSkillsDirectory = skillsRuntimeResolver.resolvedSkillsDirectory();
        SkillManager skillManager = skillsRuntimeResolver.manualSkillManager();
        int manualSkillCount = skillManager == null ? 0 : skillManager.getSkillCount();
        List<Map<String, Object>> academicTools = academicToolCallbackFactory == null
                ? List.of()
                : academicToolCallbackFactory.preview("capabilities", true);
        int offlineAcademicToolCount = academicToolCallbackFactory == null
                ? 0
                : academicToolCallbackFactory.preview("capabilities", false).size();
        Map<String, Object> agentAdminStatistics = agentAdminStatistics();
        result.put("chatModelAvailable", chatModelProvider.getIfAvailable() != null);
        result.put("tavilyConfigured", webSearchMcpClient.isTavilyConfigured());
        result.put("webSearchAvailable", webSearchMcpClient.getToolCallbacks().length > 0);
        result.put("webSearchToolCount", webSearchMcpClient.getToolCallbacks().length);
        result.put("webSearchStatus", webSearchMcpClient.getWebSearchStatus());
        result.put("reactorToolEnabled", reactorToolEnabled);
        result.put("academicToolAvailable", !academicTools.isEmpty());
        result.put("academicToolCount", academicTools.size());
        result.put("academicOfflineToolCount", offlineAcademicToolCount);
        result.put("academicTools", academicTools);
        result.put("skillsDirectoryConfigured", StringUtils.hasText(resolvedSkillsDirectory));
        result.put("skillsDirectory", resolvedSkillsDirectory);
        result.put("skillsOutputDirectory", skillsRuntimeResolver.resolvedSkillsOutputDirectory());
        result.put("skillsToolAvailable", skillsRuntimeResolver.skillsToolCallbacks().length > 0);
        result.put("manualSkillsAvailable", manualSkillCount > 0);
        result.put("manualSkillCount", manualSkillCount);
        result.put("manualSkills", manualSkillSummaries(skillManager));
        result.put("manualSkillsEndpoint", "/api/v1/academic/stream");
        result.put("manualSkillsTaskType", "manual-skills");
        result.put("agentAdmin", agentAdminStatistics);
        result.put("agentAdminConfigCount", agentAdminStatistics.getOrDefault("configCount", 0));
        result.put("agentAdminEnabledCount", agentAdminStatistics.getOrDefault("enabledCount", 0));
        result.put("quotaMode", "spring-ai-usage-with-estimated-fallback");
        result.put("apiDocs", "/swagger-ui/index.html");
        List<Map<String, Object>> agentExecutionModes = agentExecutionModes();
        result.put("agentExecutionModes", agentExecutionModes);
        List<Map<String, Object>> workspaceProfiles = workspaceProfiles(academicTools);
        List<Map<String, Object>> toolRuntimeReadiness = toolRuntimeReadiness(academicTools, workspaceProfiles);
        List<Map<String, Object>> toolRuntimeFamilies = toolRuntimeFamilies(toolRuntimeReadiness);
        Map<String, Object> mcpAdminHealth = mcpAdminHealth();
        List<Map<String, Object>> capabilityMatrix =
                capabilityMatrix(academicTools, mcpAdminHealth, agentAdminStatistics,
                        resolvedSkillsDirectory, manualSkillCount);
        result.put("workspaceProfiles", workspaceProfiles);
        result.put("toolRuntimeReadiness", toolRuntimeReadiness);
        result.put("toolRuntimeFamilies", toolRuntimeFamilies);
        result.put("capabilityMatrix", capabilityMatrix);
        result.put("mcpAdminHealth", mcpAdminHealth);
        result.put("toolCatalog", toolCatalog(academicTools, workspaceProfiles));
        result.put("agentPlatformReadiness",
                agentPlatformReadiness(agentExecutionModes, toolRuntimeReadiness, workspaceProfiles, capabilityMatrix));
        return result;
    }

    private Map<String, Object> agentAdminStatistics() {
        if (agentAdminConfigHandler == null) {
            return Map.of(
                    "configCount", 0,
                    "enabledCount", 0,
                    "disabledCount", 0,
                    "categoryCount", 0,
                    "categories", List.of());
        }
        try {
            return agentAdminConfigHandler.statistics();
        } catch (Exception e) {
            LOGGER.warn("agent admin statistics degraded, reason={}", e.getClass().getSimpleName());
            return Map.of(
                    "configCount", 0,
                    "enabledCount", 0,
                    "disabledCount", 0,
                    "categoryCount", 0,
                    "categories", List.of(),
                    "error", e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> mcpAdminHealth() {
        McpAdminHandler handler = mcpAdminHandler == null ? null : mcpAdminHandler.getIfAvailable();
        if (handler == null) {
            return Map.of(
                    "overallStatus", "missing",
                    "serverCount", 0,
                    "enabledServerCount", 0,
                    "readyServerCount", 0,
                    "degradedServerCount", 0,
                    "toolCount", 0,
                    "enabledToolCount", 0,
                    "message", "MCP admin handler is not available");
        }
        try {
            return handler.health();
        } catch (Exception e) {
            LOGGER.warn("mcp admin health degraded, reason={}", e.getClass().getSimpleName());
            return Map.of(
                    "overallStatus", "degraded",
                    "serverCount", 0,
                    "enabledServerCount", 0,
                    "readyServerCount", 0,
                    "degradedServerCount", 0,
                    "toolCount", 0,
                    "enabledToolCount", 0,
                    "error", e.getClass().getSimpleName());
        }
    }

    private List<Map<String, Object>> agentExecutionModes() {
        return List.of(
                agentExecutionMode("auto", "智能调度", "auto", "Auto", "根据问题与附件自动选择最合适的执行模式"),
                agentExecutionMode("chat", "对话助手", "react", "ReAct", "通用问答、交易解释和轻量工具调用"),
                agentExecutionMode("file", "文件问答", "react", "ReAct", "文件理解、引用回答和上下文追问"),
                agentExecutionMode("ppt", "PPT 生成", "ppt-workflow", "PPT Workflow", "需求澄清、大纲、搜索、模板和渲染的业务执行路线"),
                agentExecutionMode("deep", "深度任务", "plan-execute", "Plan-Execute", "计划拆解、分步执行、反思评估和动态重规划",
                        true,
                        List.of("plan_update/replan stream event",
                                "AcademicAgentFlowProgress.STATUS_REPLANNED",
                                "AcademicAgentFallbackReplanStrategy default recovery",
                                "planner history versions")),
                agentExecutionMode("image", "图像生成", "react", "ReAct", "图像生成、图生图和多模态参考图处理"),
                agentExecutionMode("data", "数据问答", "react", "ReAct", "数据分析、表格检索和自然语言转 SQL"),
                agentExecutionMode("skills", "技能助手", "skill-orchestration", "Skill Orchestration", "自动选择技能并组合工具完成任务"),
                agentExecutionMode("manual-skills", "手动技能", "skill-orchestration", "Skill Orchestration", "读取技能文件、检索技能目录和运行技能脚本")
        );
    }

    private Map<String, Object> agentExecutionMode(String agentId,
                                                   String name,
                                                   String family,
                                                   String executionMode,
                                                   String summary) {
        return agentExecutionMode(agentId, name, family, executionMode, summary, false, List.of());
    }

    private Map<String, Object> agentExecutionMode(String agentId,
                                                   String name,
                                                   String family,
                                                   String executionMode,
                                                   String summary,
                                                   boolean replanEnabled,
                                                   List<String> replanEvidence) {
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("agentId", agentId);
        mode.put("name", name);
        mode.put("family", family);
        mode.put("executionMode", executionMode);
        mode.put("summary", summary);
        if (replanEnabled || (replanEvidence != null && !replanEvidence.isEmpty())) {
            mode.put("replanEnabled", replanEnabled);
            mode.put("replanEvidence", replanEvidence == null ? List.of() : replanEvidence);
        }
        return mode;
    }

    private List<Map<String, Object>> manualSkillSummaries(SkillManager skillManager) {
        if (skillManager == null) {
            return List.of();
        }
        return skillManager.getSkills().stream()
                .sorted(Comparator.comparing(SkillMetadata::name))
                .map(this::manualSkillSummary)
                .toList();
    }

    private Map<String, Object> manualSkillSummary(SkillMetadata skill) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", skill.name());
        summary.put("description", skill.description());
        summary.put("descriptionZh", manualSkillDescriptionZh(skill));
        summary.put("source", skill.source() == null ? "" : skill.source().name());
        summary.put("allowedTools", skill.allowedTools() == null ? List.of() : skill.allowedTools());
        summary.put("scriptCount", skill.scripts() == null ? 0 : skill.scripts().size());
        summary.put("scripts", skill.buildScriptSummaries());
        return summary;
    }

    private String manualSkillDescriptionZh(SkillMetadata skill) {
        String description = compactSkillDescription(skill.description());
        return StringUtils.hasText(description) ? description : skill.name();
    }

    private String compactSkillDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }
        return description.replaceAll("\\s+", " ").trim();
    }

    private List<Map<String, Object>> workspaceProfiles(List<Map<String, Object>> academicTools) {
        List<String> toolNames = toolNames(academicTools);
        return List.of(
                workspaceProfile("agent", "/", "chat", "file",
                        List.of("planning", "web_fetch", "deep_search", "code_interpreter", "report_tool"),
                        List.of("answer", "reference", "artifact"), "", "", toolNames),
                workspaceProfile("image", "/workspace/image", "image", "file-or-image",
                        List.of("image_generation", "multimodal_agent", "file_tool"),
                        List.of("image", "prompt", "artifact"),
                        "/api/v1/academic/workspace/image/generate",
                        "/api/v1/academic/workspace/image/history",
                        toolNames),
                workspaceProfile("data", "/workspace/data", "data", "file",
                        List.of("data_analysis", "table_rag", "nl2sql", "report_tool"),
                        List.of("table", "sql", "chart", "report"),
                        "/api/v1/academic/workspace/data/run",
                        "/api/v1/academic/workspace/data/history",
                        toolNames),
                workspaceProfile("trade", "/workspace/trade", "trade-diagnosis", "none",
                        List.of("trade_order_list", "trade_diagnosis"),
                        List.of("order", "quota", "status", "report"),
                        "/api/v1/academic/stream", "/api/v1/trade/order/my", toolNames)
        );
    }

    private Map<String, Object> workspaceProfile(String id,
                                                 String path,
                                                 String taskType,
                                                 String attachmentMode,
                                                 List<String> primaryTools,
                                                 List<String> outputKinds,
                                                 String runEndpoint,
                                                 String historyEndpoint,
                                                 List<String> availableToolNames) {
        List<String> availableTools = primaryTools.stream()
                .filter(availableToolNames::contains)
                .toList();
        List<String> missingTools = primaryTools.stream()
                .filter(toolName -> !availableToolNames.contains(toolName))
                .toList();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", id);
        profile.put("path", path);
        profile.put("taskType", taskType);
        profile.put("attachmentMode", attachmentMode);
        profile.put("primaryTools", primaryTools);
        profile.put("availableTools", availableTools);
        profile.put("missingTools", missingTools);
        profile.put("outputKinds", outputKinds);
        profile.put("runEndpoint", StringUtils.hasText(runEndpoint) ? runEndpoint.trim() : "");
        profile.put("historyEndpoint", StringUtils.hasText(historyEndpoint) ? historyEndpoint.trim() : "");
        profile.put("status", missingTools.isEmpty() ? "ready" : availableTools.isEmpty() ? "pending" : "degraded");
        return profile;
    }

    private Map<String, Object> toolCatalog(List<Map<String, Object>> academicTools,
                                            List<Map<String, Object>> workspaceProfiles) {
        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        for (Map<String, Object> tool : academicTools == null ? List.<Map<String, Object>>of() : academicTools) {
            String category = String.valueOf(tool.getOrDefault("category", "unknown")).trim();
            String source = String.valueOf(tool.getOrDefault("source", "unknown")).trim();
            byCategory.computeIfAbsent(StringUtils.hasText(category) ? category : "unknown", key -> new ArrayList<>())
                    .add(tool);
            bySource.computeIfAbsent(StringUtils.hasText(source) ? source : "unknown", key -> new ArrayList<>())
                    .add(tool);
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("total", academicTools == null ? 0 : academicTools.size());
        catalog.put("categoryGroups", toolGroups(byCategory));
        catalog.put("sourceGroups", toolGroups(bySource));
        catalog.put("workspaceCoverage", workspaceProfiles.stream()
                .map(profile -> {
                    Map<String, Object> coverage = new LinkedHashMap<>();
                    coverage.put("workspace", profile.get("id"));
                    coverage.put("status", profile.get("status"));
                    coverage.put("runEndpoint", profile.getOrDefault("runEndpoint", ""));
                    coverage.put("historyEndpoint", profile.getOrDefault("historyEndpoint", ""));
                    coverage.put("availableTools", profile.getOrDefault("availableTools", List.of()));
                    coverage.put("missingTools", profile.getOrDefault("missingTools", List.of()));
                    return coverage;
                })
                .toList());
        return catalog;
    }

    private List<Map<String, Object>> toolGroups(Map<String, List<Map<String, Object>>> groups) {
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<String> names = entry.getValue().stream()
                            .map(tool -> String.valueOf(tool.getOrDefault("name", "")).trim())
                            .filter(StringUtils::hasText)
                            .sorted()
                            .toList();
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("key", entry.getKey());
                    group.put("count", names.size());
                    group.put("tools", names);
                    return group;
                })
                .toList();
    }

    private List<Map<String, Object>> capabilityMatrix(List<Map<String, Object>> academicTools,
                                                       Map<String, Object> mcpAdminHealth,
                                                       Map<String, Object> agentAdminStatistics,
                                                       String resolvedSkillsDirectory,
                                                       int manualSkillCount) {
        List<String> toolNames = toolNames(academicTools);
        List<String> implementedTools = AcademicToolOutputNames.orderedRichToolNames();
        List<String> missingRuntimeTools = implementedTools.stream()
                .filter(toolName -> !toolNames.contains(toolName))
                .toList();
        long mcpToolCount = academicTools.stream()
                .filter(tool -> "mcp".equalsIgnoreCase(String.valueOf(tool.getOrDefault("category", ""))))
                .count();
        String mcpOverallStatus = defaultText(mcpAdminHealth.get("overallStatus"), "missing");
        long mcpServerCount = numberValue(mcpAdminHealth.get("serverCount"));
        long mcpEnabledServerCount = numberValue(mcpAdminHealth.get("enabledServerCount"));
        long mcpReadyServerCount = numberValue(mcpAdminHealth.get("readyServerCount"));
        long mcpEnabledToolCount = numberValue(mcpAdminHealth.get("enabledToolCount"));
        boolean mcpReady = "ready".equals(mcpOverallStatus) && mcpEnabledToolCount > 0 && mcpToolCount > 0;

        return List.of(
                capabilityItem("execution-strategy", "执行策略", "ready",
                        "ReAct、Plan-Execute、PPT Workflow、Skill Orchestration、动态重规划和会话执行记忆已接入主链路。",
                        List.of("chat/file 使用 ReAct 链路", "deep 使用 Plan-Execute", "ppt 使用 PPT Workflow", "skills 使用 Skill Orchestration", "支持 plan_delta 和 flow_delta"),
                        List.of()),
                capabilityItem("tool-runtime", "工具运行时", "ready",
                        "统一工具注册、结构化输出、产物登记和运行账本已接入。",
                        mergeEvidence(implementedTools, List.of("AcademicToolRuntimeSummary 统计 total/enabled/disabled/category/source")),
                        missingRuntimeTools),
                capabilityItem("mcp", "MCP 管理", mcpReady ? "ready" : "degraded",
                        "支持服务注册、启停、工具发现、缓存和后台配置导入。",
                        List.of("管理接口: /api/v1/mcp/admin/**",
                                "MCP 健康状态: " + mcpOverallStatus,
                                "MCP 服务: " + mcpEnabledServerCount + "/" + mcpServerCount,
                                "MCP 可用服务: " + mcpReadyServerCount + "/" + mcpEnabledServerCount,
                                "MCP 管理启用工具数: " + mcpEnabledToolCount,
                                "已桥接到 Agent 的 MCP 工具数: " + mcpToolCount),
                        mcpGaps(mcpOverallStatus, mcpServerCount, mcpEnabledServerCount, mcpEnabledToolCount, mcpToolCount)),
                capabilityItem("agent-admin", "Agent Admin Config", "ready",
                        "支持 Agent 客户端、API、模型、系统提示词、增强策略、RAG 顺序、工具、MCP 工具和绘图配置。",
                        List.of("/api/v1/agent/admin/configs",
                                "/api/v1/agent/admin/statistics",
                                "/api/v1/agent/admin/assembly",
                                "configCount=" + agentAdminStatistics.getOrDefault("configCount", 0)),
                        List.of()),
                capabilityItem("workspace", "前端工作区", "ready",
                        "前端已提供 Agent、图像生成、数据问答和拼团交易工作区。",
                        List.of("/", "/workspace/image", "/workspace/data", "/workspace/trade"),
                        List.of()),
                capabilityItem("skill-runtime", "Skill 与脚本", StringUtils.hasText(resolvedSkillsDirectory) ? "ready" : "degraded",
                        "支持手动技能读取、技能目录检索、脚本定义和会话产物目录。",
                        List.of("manualSkillCount=" + manualSkillCount,
                                "tools=read_skill/read_skill_file/grep_skill_files/glob_skill_files/list_skill_directory",
                                "skillsDirectory=" + (resolvedSkillsDirectory == null ? "" : resolvedSkillsDirectory)),
                        StringUtils.hasText(resolvedSkillsDirectory) ? List.of() : List.of("未配置 skills.directory")),
                capabilityItem("trade-quota", "交易与额度闭环", "ready",
                        "直接购买、拼团成团、支付状态、额度发放、任务扣减和退款回滚由后端交易系统控制。",
                        List.of("额度扣减模式: spring-ai-usage-with-estimated-fallback",
                                "直购 PAY_SUCCESS 后可发放额度",
                                "拼团 GROUP_SETTLED/DEAL_DONE 后才可发放额度",
                                "前端交易工作区: /workspace/trade"),
                        List.of())
        );
    }

    private List<String> mergeEvidence(List<String> primary, List<String> additional) {
        List<String> evidence = new ArrayList<>();
        if (primary != null) {
            evidence.addAll(primary);
        }
        if (additional != null) {
            evidence.addAll(additional);
        }
        return evidence;
    }

    private List<String> mcpGaps(String overallStatus,
                                 long serverCount,
                                 long enabledServerCount,
                                 long enabledToolCount,
                                 long bridgedToolCount) {
        List<String> gaps = new ArrayList<>();
        if ("missing".equals(overallStatus)) {
            gaps.add("MCP 管理器未加载");
        }
        if (serverCount == 0) {
            gaps.add("还没有注册 MCP 服务");
        } else if (enabledServerCount == 0) {
            gaps.add("没有启用 MCP 服务");
        }
        if (enabledServerCount > 0 && enabledToolCount == 0) {
            gaps.add("当前没有可供 Agent 使用的 MCP 工具");
        }
        if (bridgedToolCount == 0) {
            gaps.add("当前未发现或未缓存外部 MCP 工具");
        }
        if (StringUtils.hasText(overallStatus)
                && !"ready".equals(overallStatus)
                && !"missing".equals(overallStatus)
                && serverCount > 0) {
            gaps.add("MCP 服务健康状态为 " + overallStatus);
        }
        return gaps.stream().distinct().toList();
    }

    private List<Map<String, Object>> tradeQuotaSettlementRules() {
        return List.of(
                tradeQuotaSettlementRule("direct-pay-success", "直购支付成功", "PAY_SUCCESS", true,
                        "直购订单支付成功后可以发放额度，但仍以后端支付单和额度流水为准。"),
                tradeQuotaSettlementRule("group-pay-success", "拼团名额已支付", "PAY_SUCCESS", false,
                        "拼团支付成功只表示名额已支付，未成团前不能发放额度。"),
                tradeQuotaSettlementRule("group-settled", "拼团已成团", "GROUP_SETTLED/DEAL_DONE", true,
                        "拼团已成团或交易完成后，才能给同团用户发放额度。"),
                tradeQuotaSettlementRule("refund-success", "退款成功", "REFUND_SUCCESS/REFUNDED", false,
                        "退款或误发时必须记录额度流水并回滚余额。")
        );
    }

    private Map<String, Object> tradeQuotaSettlementRule(String key,
                                                         String scenario,
                                                         String requiredState,
                                                         boolean quotaGrantAllowed,
                                                         String operatorHint) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("key", key);
        rule.put("scenario", scenario);
        rule.put("requiredState", requiredState);
        rule.put("quotaGrantAllowed", quotaGrantAllowed);
        rule.put("operatorHint", operatorHint);
        return rule;
    }

    private List<Map<String, Object>> toolRuntimeReadiness(List<Map<String, Object>> academicTools,
                                                           List<Map<String, Object>> workspaceProfiles) {
        List<String> availableToolNames = toolNames(academicTools);
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> tool : academicTools == null ? List.<Map<String, Object>>of() : academicTools) {
            String name = String.valueOf(tool.getOrDefault("name", "")).trim();
            if (StringUtils.hasText(name)) {
                byName.putIfAbsent(name, tool);
            }
        }
        return AcademicToolOutputNames.orderedRichToolNames().stream()
                .map(toolName -> {
                    boolean ready = availableToolNames.contains(toolName);
                    Map<String, Object> source = byName.getOrDefault(toolName, Map.of());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", toolName);
                    item.put("status", ready ? "ready" : "missing");
                    item.put("category", String.valueOf(source.getOrDefault("category", categoryForTool(toolName))));
                    item.put("source", String.valueOf(source.getOrDefault("source", ready ? "runtime" : "port")));
                    item.put("requiredArguments", source.getOrDefault("requiredArguments", List.of()));
                    item.put("inputFields", source.getOrDefault("inputFields", fallbackInputFields(toolName)));
                    item.put("outputKinds", outputKindsForTool(toolName));
                    item.put("workspaces", workspacesUsingTool(toolName, workspaceProfiles));
                    item.put("message", ready ? "registered" : "external port is not configured");
                    item.put("hint", ready ? "" : toolRuntimeHint(toolName));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> toolRuntimeFamilies(List<Map<String, Object>> toolRuntimeReadiness) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> item : toolRuntimeReadiness == null ? List.<Map<String, Object>>of() : toolRuntimeReadiness) {
            String name = text(item.get("name"));
            if (StringUtils.hasText(name)) {
                byName.put(name, item);
            }
        }
        return List.of(
                toolRuntimeFamily("web", "网页抓取", List.of(AcademicToolOutputNames.WEB_FETCH, AcademicToolOutputNames.DEEP_SEARCH), byName),
                toolRuntimeFamily("data", "数据分析", List.of(AcademicToolOutputNames.DATA_ANALYSIS, AcademicToolOutputNames.TABLE_RAG, AcademicToolOutputNames.NL2SQL), byName),
                toolRuntimeFamily("image", "图像生成", List.of(AcademicToolOutputNames.IMAGE_GENERATION), byName),
                toolRuntimeFamily("report", "报告工具", List.of(AcademicToolOutputNames.REPORT_TOOL), byName),
                toolRuntimeFamily("code", "代码解释器", List.of(AcademicToolOutputNames.CODE_INTERPRETER, AcademicToolOutputNames.SCRIPT_RUNNER), byName),
                toolRuntimeFamily("multimodal", "多模态", List.of(AcademicToolOutputNames.MULTIMODAL_AGENT, AcademicToolOutputNames.FILE_TOOL), byName)
        );
    }

    private Map<String, Object> toolRuntimeFamily(String key,
                                                  String label,
                                                  List<String> tools,
                                                  Map<String, Map<String, Object>> byName) {
        List<String> readyTools = tools.stream()
                .filter(toolName -> "ready".equals(text(byName.getOrDefault(toolName, Map.of()).get("status"))))
                .toList();
        List<String> missingTools = tools.stream()
                .filter(toolName -> !readyTools.contains(toolName))
                .toList();
        List<String> outputKinds = tools.stream()
                .flatMap(toolName -> stringValues(byName.getOrDefault(toolName, Map.of()).get("outputKinds")).stream())
                .distinct()
                .limit(5)
                .toList();
        List<String> workspaces = tools.stream()
                .flatMap(toolName -> stringValues(byName.getOrDefault(toolName, Map.of()).get("workspaces")).stream())
                .distinct()
                .limit(5)
                .toList();
        String status = readyTools.isEmpty() ? "missing" : missingTools.isEmpty() ? "ready" : "partial";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", status);
        item.put("statusLabel", toolRuntimeFamilyStatusLabel(status));
        item.put("readyCount", readyTools.size());
        item.put("totalCount", tools.size());
        item.put("tools", tools);
        item.put("missingTools", missingTools);
        item.put("outputKinds", outputKinds);
        item.put("workspaces", workspaces);
        item.put("action", missingTools.isEmpty() ? "核心工具已覆盖" : "补齐 " + compactLabels(missingTools, 2) + " 工具运行时");
        return item;
    }

    private String toolRuntimeFamilyStatusLabel(String status) {
        return switch (status) {
            case "ready" -> "已就绪";
            case "partial" -> "部分就绪";
            default -> "未就绪";
        };
    }

    private Map<String, Object> agentPlatformReadiness(List<Map<String, Object>> executionModes,
                                                       List<Map<String, Object>> toolRuntimeReadiness,
                                                       List<Map<String, Object>> workspaceProfiles,
                                                       List<Map<String, Object>> capabilityMatrix) {
        List<String> requiredFamilies = List.of("react", "plan-execute", "flow", "skill-sop");
        List<String> coveredFamilies = executionModes.stream()
                .map(mode -> text(mode.get("family")))
                .filter(requiredFamilies::contains)
                .distinct()
                .toList();
        List<String> missingFamilies = requiredFamilies.stream()
                .filter(family -> !coveredFamilies.contains(family))
                .toList();
        long replanModeCount = executionModes.stream()
                .filter(mode -> truthy(mode.get("dynamicReplan")))
                .count();
        List<String> orderedTools = AcademicToolOutputNames.orderedRichToolNames();
        List<String> readyTools = toolRuntimeReadiness.stream()
                .filter(item -> "ready".equals(text(item.get("status"))))
                .map(item -> text(item.get("name")))
                .filter(orderedTools::contains)
                .distinct()
                .toList();
        List<String> missingTools = orderedTools.stream()
                .filter(tool -> !readyTools.contains(tool))
                .toList();
        List<String> requiredWorkspaces = List.of("agent", "image", "data", "trade");
        List<String> coveredWorkspaces = workspaceProfiles.stream()
                .filter(this::workspaceEntryReady)
                .map(profile -> text(profile.get("id")))
                .filter(requiredWorkspaces::contains)
                .distinct()
                .toList();
        List<String> missingWorkspaces = requiredWorkspaces.stream()
                .filter(workspace -> !coveredWorkspaces.contains(workspace))
                .toList();
        Map<String, Object> mcp = capabilityByKey(capabilityMatrix, "mcp");
        List<String> mcpGaps = mcp.isEmpty() ? List.of("MCP 管理能力未接入") : stringValues(mcp.get("gaps"));
        String mcpStatus = defaultText(mcp.get("status"), mcpGaps.isEmpty() ? "ready" : "degraded");
        Map<String, Object> tradeQuota = capabilityByKey(capabilityMatrix, "trade-quota");
        List<Map<String, Object>> settlementRules = mapList(tradeQuota.get("settlementRules"));
        long blockedSettlementRuleCount = settlementRules.stream()
                .filter(rule -> !truthy(rule.get("quotaGrantAllowed")))
                .count();
        List<String> guardrails = stringValues(tradeQuota.get("guardrails"));
        boolean ready = missingFamilies.isEmpty()
                && replanModeCount > 0
                && missingTools.isEmpty()
                && missingWorkspaces.isEmpty()
                && mcpGaps.isEmpty()
                && !settlementRules.isEmpty()
                && blockedSettlementRuleCount > 0;
        String status = executionModes.isEmpty() ? "missing" : ready ? "ready" : "partial";
        List<String> gaps = new ArrayList<>();
        if (!missingFamilies.isEmpty()) {
            gaps.add("缺少执行族：" + String.join("、", missingFamilies));
        }
        if (replanModeCount == 0) {
            gaps.add("缺少动态重规划证据");
        }
        if (!missingTools.isEmpty()) {
            gaps.add("工具运行时未全部就绪：" + compactLabels(missingTools, 4));
        }
        if (!missingWorkspaces.isEmpty()) {
            gaps.add("工作区入口未完整：" + String.join("、", missingWorkspaces));
        }
        gaps.addAll(mcpGaps);
        if (settlementRules.isEmpty()) {
            gaps.add("缺少拼团额度发放规则");
        }
        List<String> actions = new ArrayList<>();
        if (!missingTools.isEmpty()) {
            actions.add("启动或配置工具运行时：" + compactLabels(missingTools, 3));
        }
        if (!mcpGaps.isEmpty()) {
            actions.add("注册、发现并缓存 MCP 工具");
        }
        if (replanModeCount == 0 || !missingFamilies.isEmpty()) {
            actions.add("补齐执行策略与重规划证据");
        }
        if (settlementRules.isEmpty()) {
            actions.add("补齐拼团额度发放规则");
        }
        if (ready) {
            actions.add("Agent 与拼团交易闭环已具备完整演示面");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("statusLabel", platformReadinessStatusLabel(status));
        result.put("title", "Agent + 拼团交易系统就绪度");
        result.put("metrics", List.of(
                readinessMetric("families", "执行族", coveredFamilies.size() + "/" + requiredFamilies.size(), missingFamilies.isEmpty() ? "good" : "warn"),
                readinessMetric("replan", "重规划", String.valueOf(replanModeCount), replanModeCount > 0 ? "good" : "warn"),
                readinessMetric("tools", "工具", readyTools.size() + "/" + orderedTools.size(), missingTools.isEmpty() ? "good" : "warn"),
                readinessMetric("workspaces", "工作区", coveredWorkspaces.size() + "/" + requiredWorkspaces.size(), missingWorkspaces.isEmpty() ? "good" : "warn"),
                readinessMetric("tradeRules", "交易规则", String.valueOf(settlementRules.size()), settlementRules.isEmpty() ? "warn" : "good")
        ));
        result.put("coveredFamilies", coveredFamilies);
        result.put("missingFamilies", missingFamilies);
        result.put("replanModeCount", replanModeCount);
        result.put("readyToolCount", readyTools.size());
        result.put("requiredToolCount", orderedTools.size());
        result.put("missingTools", missingTools);
        result.put("coveredWorkspaces", coveredWorkspaces);
        result.put("missingWorkspaces", missingWorkspaces);
        result.put("mcpStatus", mcpStatus);
        result.put("mcpGaps", mcpGaps);
        result.put("settlementRuleCount", settlementRules.size());
        result.put("blockedSettlementRuleCount", blockedSettlementRuleCount);
        result.put("tradeGuardrails", guardrails);
        result.put("gaps", gaps);
        result.put("actions", actions);
        return result;
    }

    private List<String> fallbackInputFields(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> List.of("url", "query");
            case AcademicToolOutputNames.DATA_ANALYSIS -> List.of("task", "rows", "columns");
            case AcademicToolOutputNames.REPORT_TOOL -> List.of("title", "content", "format");
            case AcademicToolOutputNames.PLANNING -> List.of("goal", "context");
            case AcademicToolOutputNames.CODE_INTERPRETER -> List.of("task", "language", "code");
            case AcademicToolOutputNames.IMAGE_GENERATION -> List.of("prompt", "model", "quality", "aspectRatio", "size", "mode");
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> List.of("question", "imageUrls", "fileIds");
            case AcademicToolOutputNames.DEEP_SEARCH -> List.of("query", "scope");
            case AcademicToolOutputNames.FILE_TOOL -> List.of("action", "fileId", "fileName");
            case AcademicToolOutputNames.SCRIPT_RUNNER -> List.of("scriptName", "runtime", "arguments");
            case AcademicToolOutputNames.TABLE_RAG -> List.of("query", "modelCodeList");
            case AcademicToolOutputNames.NL2SQL -> List.of("query", "schemaInfo");
            default -> List.of();
        };
    }

    private List<String> outputKindsForTool(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> List.of("web", "file");
            case AcademicToolOutputNames.DATA_ANALYSIS -> List.of("table", "summary");
            case AcademicToolOutputNames.REPORT_TOOL -> List.of("report", "artifact");
            case AcademicToolOutputNames.PLANNING -> List.of("plan", "flow");
            case AcademicToolOutputNames.CODE_INTERPRETER -> List.of("code", "file", "summary");
            case AcademicToolOutputNames.IMAGE_GENERATION -> List.of("image", "artifact");
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> List.of("multimodal", "evidence");
            case AcademicToolOutputNames.DEEP_SEARCH -> List.of("answer", "reference");
            case AcademicToolOutputNames.FILE_TOOL -> List.of("file", "content");
            case AcademicToolOutputNames.SCRIPT_RUNNER -> List.of("script", "artifact");
            case AcademicToolOutputNames.TABLE_RAG -> List.of("schema", "evidence");
            case AcademicToolOutputNames.NL2SQL -> List.of("sql", "table");
            default -> List.of("result");
        };
    }

    private List<String> workspacesUsingTool(String toolName, List<Map<String, Object>> workspaceProfiles) {
        if (!StringUtils.hasText(toolName) || workspaceProfiles == null) {
            return List.of();
        }
        return workspaceProfiles.stream()
                .filter(profile -> profile.get("primaryTools") instanceof List<?> tools
                        && tools.stream().map(String::valueOf).anyMatch(toolName::equals))
                .map(profile -> String.valueOf(profile.getOrDefault("id", "")).trim())
                .filter(StringUtils::hasText)
                .toList();
    }

    private String toolRuntimeHint(String toolName) {
        String base = "Start tools/reactor-tool/start.ps1 and set AGENT_GROUP_REACTOR_TOOL_ENABLED=true, AGENT_GROUP_REACTOR_TOOL_BASE_URL=http://127.0.0.1:1801.";
        return switch (toolName) {
            case AcademicToolOutputNames.IMAGE_GENERATION -> base + " Image generation also requires model and storage config in reactor-tool .env.";
            case AcademicToolOutputNames.DATA_ANALYSIS,
                 AcademicToolOutputNames.TABLE_RAG,
                 AcademicToolOutputNames.NL2SQL -> base + " Data tools require table, database, or vector retrieval config.";
            case AcademicToolOutputNames.DEEP_SEARCH,
                 AcademicToolOutputNames.WEB_FETCH -> base + " Search tools require an external search provider; direct search is used as fallback.";
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> base + " Multimodal tools require file parsing, table understanding, and image understanding capability.";
            case AcademicToolOutputNames.SCRIPT_RUNNER,
                 AcademicToolOutputNames.CODE_INTERPRETER -> base + " Script tools require a local Python/code execution runtime.";
            default -> base;
        };
    }
    private String categoryForTool(String toolName) {
        return switch (toolName) {
            case AcademicToolOutputNames.WEB_FETCH -> "web";
            case AcademicToolOutputNames.DEEP_SEARCH -> "search";
            case AcademicToolOutputNames.DATA_ANALYSIS,
                 AcademicToolOutputNames.TABLE_RAG,
                 AcademicToolOutputNames.NL2SQL -> "data";
            case AcademicToolOutputNames.REPORT_TOOL -> "report";
            case AcademicToolOutputNames.PLANNING -> "planning";
            case AcademicToolOutputNames.CODE_INTERPRETER -> "code";
            case AcademicToolOutputNames.IMAGE_GENERATION -> "image";
            case AcademicToolOutputNames.MULTIMODAL_AGENT -> "multimodal";
            case AcademicToolOutputNames.FILE_TOOL -> "file";
            case AcademicToolOutputNames.SCRIPT_RUNNER -> "skill";
            default -> "tool";
        };
    }

    private Map<String, Object> capabilityItem(String key,
                                               String label,
                                               String status,
                                               String summary,
                                               List<String> evidence,
                                               List<String> gaps) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", status);
        item.put("summary", summary);
        item.put("evidence", evidence == null ? List.of() : evidence);
        item.put("gaps", gaps == null ? List.of() : gaps);
        if ("multi-agent".equals(key)) {
            Map<String, Object> dynamicReplan = new LinkedHashMap<>();
            dynamicReplan.put("enabled", true);
            dynamicReplan.put("executionModes", List.of("deep"));
            dynamicReplan.put("streamEvents", List.of("plan_delta:replan", "flow_delta:REPLANNED"));
            dynamicReplan.put("historyEvidence", List.of("AcademicReplayProjector", "planner history versions", "AcademicAgentFallbackReplanStrategy"));
            item.put("dynamicReplan", dynamicReplan);
        }
        if ("trade-quota".equals(key)) {
            item.put("authoritativeSources", List.of("trade_order", "pay_order", "group_buy_team", "quota_flow"));
            item.put("userAgentExposure", "backend_only");
            item.put("settlementRules", tradeQuotaSettlementRules());
            item.put("guardrails", List.of(
                    "前端和 Agent 不能直接决定额度到账",
                    "拼团支付成功不等于额度到账",
                    "高风险状态必须来自后端交易系统"));
        }
        if ("tool-runtime".equals(key)) {
            List<String> implementedTools = evidence == null ? List.of() : evidence;
            List<String> missingRuntimeTools = gaps == null ? List.of() : gaps;
            item.put("implementedTools", implementedTools);
            item.put("runtimeEnabledTools", implementedTools.stream()
                    .filter(toolName -> !missingRuntimeTools.contains(toolName))
                    .toList());
            item.put("missingRuntimeTools", missingRuntimeTools);
            item.put("gaps", List.of());
        }
        return item;
    }

    private List<String> toolNames(List<Map<String, Object>> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> String.valueOf(tool.getOrDefault("name", "")).trim())
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> capabilityByKey(List<Map<String, Object>> capabilityMatrix, String key) {
        if (capabilityMatrix == null || !StringUtils.hasText(key)) {
            return Map.of();
        }
        return capabilityMatrix.stream()
                .filter(item -> key.equals(text(item.get("key"))))
                .findFirst()
                .orElse(Map.of());
    }

    private boolean workspaceEntryReady(Map<String, Object> profile) {
        if (profile == null) {
            return false;
        }
        String id = text(profile.get("id"));
        return StringUtils.hasText(text(profile.get("runEndpoint")))
                || ("agent".equals(id) && "/".equals(text(profile.get("path"))));
    }

    private List<String> stringValues(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> source) {
                Map<String, Object> map = new LinkedHashMap<>();
                source.forEach((key, val) -> map.put(String.valueOf(key), val));
                result.add(map);
            }
        }
        return result;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value).toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private String compactLabels(List<String> values, int limit) {
        List<String> cleanValues = values == null
                ? List.of()
                : values.stream().filter(StringUtils::hasText).distinct().toList();
        if (cleanValues.isEmpty()) {
            return "";
        }
        int safeLimit = Math.max(1, limit);
        List<String> visible = cleanValues.stream().limit(safeLimit).toList();
        int more = Math.max(0, cleanValues.size() - visible.size());
        return String.join("、", visible) + (more > 0 ? " 等" + more + "项" : "");
    }

    private Map<String, Object> readinessMetric(String key, String label, String value, String tone) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("key", key);
        metric.put("label", label);
        metric.put("value", value);
        metric.put("tone", tone);
        return metric;
    }

    private String platformReadinessStatusLabel(String status) {
        return switch (status) {
            case "ready" -> "ready";
            case "missing" -> "missing config";
            default -> "checking";
        };
    }

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
