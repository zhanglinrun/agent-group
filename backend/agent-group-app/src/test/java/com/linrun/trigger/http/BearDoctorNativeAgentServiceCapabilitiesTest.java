package com.linrun.trigger.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;
import com.linrun.trigger.agent.tool.AcademicToolCallbackFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearDoctorNativeAgentServiceCapabilitiesTest {

    @Test
    void shouldExposeAgentCapabilityMatrix() {
        ObjectMapper objectMapper = new ObjectMapper();
        AcademicToolCallbackFactory toolCallbackFactory = new AcademicToolCallbackFactory(
                objectMapper, null, null, null, null, null, null, null, null, null, null, null, null, null);
        BearDoctorNativeAgentService service = service(toolCallbackFactory);

        Map<String, Object> capabilities = service.capabilities();

        assertTrue((Integer) capabilities.get("agentAdminConfigCount") >= 7);
        assertTrue((Integer) capabilities.get("agentAdminEnabledCount") >= 7);
        Map<String, Object> agentAdminStatistics = (Map<String, Object>) capabilities.get("agentAdmin");
        assertTrue(((Map<?, ?>) agentAdminStatistics.get("categoryCounts")).containsKey("system_prompt"));

        assertNotNull(capabilities.get("capabilityMatrix"));
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) capabilities.get("capabilityMatrix");
        assertTrue(matrix.stream().anyMatch(item -> "multi-agent".equals(item.get("key"))));
        assertTrue(matrix.stream().anyMatch(item -> "mcp".equals(item.get("key"))));
        assertTrue(matrix.stream().anyMatch(item -> "agent-admin".equals(item.get("key"))));
        assertTrue(matrix.stream().anyMatch(item -> "workspace".equals(item.get("key"))));
        assertTrue(matrix.stream().anyMatch(item -> "trade-quota".equals(item.get("key"))));

        assertNotNull(capabilities.get("agentExecutionModes"));
        List<Map<String, Object>> agentExecutionModes =
                (List<Map<String, Object>>) capabilities.get("agentExecutionModes");
        assertTrue(agentExecutionModes.stream().anyMatch(item ->
                "deep".equals(item.get("agentId"))
                        && "plan-execute".equals(item.get("family"))
                        && "Plan Execute".equals(item.get("executionMode"))));
        Map<String, Object> deepMode = agentExecutionModes.stream()
                .filter(item -> "deep".equals(item.get("agentId")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, deepMode.get("replanEnabled"));
        assertTrue(((List<?>) deepMode.get("replanEvidence")).contains("plan_update/replan stream event"));
        assertTrue(((List<?>) deepMode.get("replanEvidence")).contains("AcademicAgentFlowProgress.STATUS_REPLANNED"));
        assertTrue(agentExecutionModes.stream().anyMatch(item ->
                "ppt".equals(item.get("agentId"))
                        && "flow".equals(item.get("family"))
                        && "Flow".equals(item.get("executionMode"))));
        assertTrue(agentExecutionModes.stream().anyMatch(item ->
                "trade-audit".equals(item.get("agentId"))
                        && "flow".equals(item.get("family"))
                        && "Trade Flow".equals(item.get("executionMode"))));
        assertTrue(agentExecutionModes.stream().anyMatch(item ->
                "chat".equals(item.get("agentId"))
                        && "react".equals(item.get("family"))));

        Map<String, Object> multiAgent = matrix.stream()
                .filter(item -> "multi-agent".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> dynamicReplan = (Map<String, Object>) multiAgent.get("dynamicReplan");
        assertEquals(true, dynamicReplan.get("enabled"));
        assertTrue(((List<?>) dynamicReplan.get("executionModes")).contains("deep"));
        assertTrue(((List<?>) dynamicReplan.get("streamEvents")).contains("flow_delta:REPLANNED"));
        assertTrue(((List<?>) multiAgent.get("evidence"))
                .contains("AcademicReActExecutionService 记录 thought/action/observation"));
        assertTrue(((List<?>) multiAgent.get("evidence")).contains("plan_delta 支持 replan 计划版本"));

        Map<String, Object> mcp = matrix.stream()
                .filter(item -> "mcp".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertTrue(((List<?>) mcp.get("evidence")).contains("后台配置: agent.group.mcp.servers"));
        assertTrue(((List<?>) mcp.get("evidence")).contains("状态文件: agent.group.mcp.admin-state-file"));

        Map<String, Object> agentAdmin = matrix.stream()
                .filter(item -> "agent-admin".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals("ready", agentAdmin.get("status"));
        assertTrue(((List<?>) agentAdmin.get("evidence")).contains("/api/v1/agent/admin/configs"));
        assertTrue(((List<?>) agentAdmin.get("evidence")).contains("configCount=7"));
        assertTrue(((List<?>) agentAdmin.get("evidence")).contains("enabledCount=7"));
        assertTrue(((List<?>) agentAdmin.get("evidence"))
                .contains("categories=agent_client,model,api,system_prompt,advisor,rag_order,draw_config"));

        Map<String, Object> toolRuntime = matrix.stream()
                .filter(item -> "tool-runtime".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals("ready", toolRuntime.get("status"));
        assertTrue(((List<?>) toolRuntime.get("evidence")).contains("data_analysis"));
        assertTrue(((List<?>) toolRuntime.get("evidence")).contains("code_interpreter"));
        assertTrue(((List<?>) toolRuntime.get("runtimeEnabledTools")).contains("data_analysis"));
        assertTrue(((List<?>) toolRuntime.get("missingRuntimeTools")).contains("code_interpreter"));
        assertTrue(((List<?>) toolRuntime.get("gaps")).isEmpty());

        Map<String, Object> tradeQuota = matrix.stream()
                .filter(item -> "trade-quota".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertTrue(((List<?>) tradeQuota.get("authoritativeSources")).contains("quota_flow"));
        assertTrue(((List<?>) tradeQuota.get("guardrails")).contains("拼团支付成功不等于额度到账"));
        List<Map<String, Object>> settlementRules = (List<Map<String, Object>>) tradeQuota.get("settlementRules");
        Map<String, Object> groupPaySuccess = settlementRules.stream()
                .filter(item -> "group-pay-success".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals("PAY_SUCCESS", groupPaySuccess.get("requiredState"));
        assertEquals(false, groupPaySuccess.get("quotaGrantAllowed"));
        assertTrue(String.valueOf(groupPaySuccess.get("operatorHint")).contains("未成团前不能发放额度"));
        Map<String, Object> groupSettled = settlementRules.stream()
                .filter(item -> "group-settled".equals(item.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, groupSettled.get("quotaGrantAllowed"));
        assertTrue(String.valueOf(groupSettled.get("requiredState")).contains("GROUP_SETTLED"));

        assertNotNull(capabilities.get("toolRuntimeReadiness"));
        List<Map<String, Object>> readiness = (List<Map<String, Object>>) capabilities.get("toolRuntimeReadiness");
        assertEquals(AcademicToolOutputNames.orderedRichToolNames().size(), readiness.size());
        assertEquals("ready", toolReadiness(readiness, "data_analysis").get("status"));
        assertEquals("missing", toolReadiness(readiness, "code_interpreter").get("status"));
        assertEquals("external port is not configured", toolReadiness(readiness, "code_interpreter").get("message"));
        assertTrue(((List<?>) toolReadiness(readiness, "code_interpreter").get("inputFields")).contains("task"));
        assertTrue(((List<?>) toolReadiness(readiness, "code_interpreter").get("outputKinds")).contains("code"));
        assertTrue(((List<?>) toolReadiness(readiness, "code_interpreter").get("workspaces")).contains("agent"));

        assertNotNull(capabilities.get("workspaceProfiles"));
        List<Map<String, Object>> workspaceProfiles = (List<Map<String, Object>>) capabilities.get("workspaceProfiles");
        Map<String, Object> agentWorkspace = workspace(workspaceProfiles, "agent");
        assertTrue(((List<?>) agentWorkspace.get("primaryTools")).contains("code_interpreter"));
        assertTrue(((List<?>) agentWorkspace.get("missingTools")).contains("code_interpreter"));

        Map<String, Object> dataWorkspace = workspaceProfiles.stream()
                .filter(item -> "data".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("/workspace/data", dataWorkspace.get("path"));
        assertEquals("data", dataWorkspace.get("taskType"));
        assertEquals("/api/v1/academic/workspace/data/run", dataWorkspace.get("runEndpoint"));
        assertEquals("/api/v1/academic/workspace/data/history", dataWorkspace.get("historyEndpoint"));
        assertTrue(((List<?>) dataWorkspace.get("primaryTools")).contains("nl2sql"));
        assertTrue(((List<?>) dataWorkspace.get("availableTools")).contains("data_analysis"));
        assertTrue(((List<?>) dataWorkspace.get("missingTools")).contains("nl2sql"));

        Map<String, Object> imageWorkspace = workspaceProfiles.stream()
                .filter(item -> "image".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("file-or-image", imageWorkspace.get("attachmentMode"));
        assertEquals("/api/v1/academic/workspace/image/generate", imageWorkspace.get("runEndpoint"));
        assertTrue(((List<?>) imageWorkspace.get("primaryTools")).contains("image_generation"));

        Map<String, Object> mragWorkspace = workspaceProfiles.stream()
                .filter(item -> "mrag".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("/api/v1/academic/workspace/mrag/run", mragWorkspace.get("runEndpoint"));
        assertEquals("/api/v1/academic/workspace/mrag/history", mragWorkspace.get("historyEndpoint"));

        Map<String, Object> tradeWorkspace = workspaceProfiles.stream()
                .filter(item -> "trade".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("trade-audit", tradeWorkspace.get("taskType"));
        assertEquals("/api/v1/academic/stream", tradeWorkspace.get("runEndpoint"));
        assertTrue(((List<?>) tradeWorkspace.get("primaryTools")).contains("trade_audit"));
        assertTrue(((List<?>) tradeWorkspace.get("primaryTools")).contains("nl2sql"));

        assertNotNull(capabilities.get("toolCatalog"));
        Map<String, Object> toolCatalog = (Map<String, Object>) capabilities.get("toolCatalog");
        assertTrue((Integer) toolCatalog.get("total") > 0);
        List<Map<String, Object>> categoryGroups = (List<Map<String, Object>>) toolCatalog.get("categoryGroups");
        assertTrue(categoryGroups.stream().anyMatch(group -> "analysis".equals(group.get("key"))));
        List<Map<String, Object>> workspaceCoverage = (List<Map<String, Object>>) toolCatalog.get("workspaceCoverage");
        assertTrue(workspaceCoverage.stream().anyMatch(coverage -> "data".equals(coverage.get("workspace"))));
        assertTrue(workspaceCoverage.stream().anyMatch(coverage ->
                "/api/v1/academic/workspace/mrag/run".equals(coverage.get("runEndpoint"))));

        assertNotNull(capabilities.get("manualSkills"));
        List<Map<String, Object>> manualSkills = (List<Map<String, Object>>) capabilities.get("manualSkills");
        assertTrue(manualSkills.size() >= 14);
        assertTrue(manualSkills.stream().anyMatch(skill -> "chart-visualization".equals(skill.get("name"))));
        assertTrue(manualSkills.stream().anyMatch(skill -> "data-analysis".equals(skill.get("name"))));
        Map<String, Object> huchenfengSkill = manualSkills.stream()
                .filter(skill -> "huchenfeng-perspective".equals(skill.get("name")))
                .findFirst()
                .orElseThrow();
        assertTrue((Integer) huchenfengSkill.get("scriptCount") >= 2);
    }

    @Test
    void shouldExposeFullRuntimeReadinessWhenExternalPortsAreConfigured() {
        ObjectMapper objectMapper = new ObjectMapper();
        AcademicToolCallbackFactory toolCallbackFactory = new AcademicToolCallbackFactory(
                objectMapper,
                provider(codePort()),
                provider(webFetchPort()),
                provider(dataAnalysisPort()),
                provider(reportPort()),
                provider(imagePort()),
                provider(multimodalPort()),
                provider(deepSearchPort()),
                provider(fileToolPort()),
                provider(scriptPort()),
                provider(tableRagPort()),
                provider(nl2SqlPort()),
                provider(tradeAuditPort()),
                null);
        BearDoctorNativeAgentService service = service(toolCallbackFactory);

        Map<String, Object> capabilities = service.capabilities();
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) capabilities.get("capabilityMatrix");
        Map<String, Object> toolRuntime = matrix.stream()
                .filter(item -> "tool-runtime".equals(item.get("key")))
                .findFirst()
                .orElseThrow();

        List<String> implementedTools = AcademicToolOutputNames.orderedRichToolNames();
        assertEquals("ready", toolRuntime.get("status"));
        assertTrue(((List<?>) toolRuntime.get("runtimeEnabledTools")).containsAll(implementedTools));
        assertTrue(((List<?>) toolRuntime.get("missingRuntimeTools")).isEmpty());
        assertTrue(((List<?>) toolRuntime.get("gaps")).isEmpty());

        List<Map<String, Object>> readiness = (List<Map<String, Object>>) capabilities.get("toolRuntimeReadiness");
        assertEquals(implementedTools.size(), readiness.size());
        assertTrue(readiness.stream().allMatch(item -> "ready".equals(item.get("status"))));
        assertTrue(((List<?>) toolReadiness(readiness, "code_interpreter").get("workspaces")).contains("agent"));
        assertTrue(((List<?>) toolReadiness(readiness, "data_analysis").get("inputFields")).contains("task"));
        assertTrue(((List<?>) toolReadiness(readiness, "image_generation").get("outputKinds")).contains("image"));

        List<Map<String, Object>> workspaceProfiles = (List<Map<String, Object>>) capabilities.get("workspaceProfiles");
        Map<String, Object> agentWorkspace = workspace(workspaceProfiles, "agent");
        assertEquals("ready", agentWorkspace.get("status"));
        assertTrue(((List<?>) agentWorkspace.get("availableTools")).contains("code_interpreter"));

        Map<String, Object> dataWorkspace = workspace(workspaceProfiles, "data");
        assertEquals("ready", dataWorkspace.get("status"));
        assertTrue(((List<?>) dataWorkspace.get("missingTools")).isEmpty());
        assertTrue(((List<?>) dataWorkspace.get("availableTools")).contains("nl2sql"));

        Map<String, Object> imageWorkspace = workspace(workspaceProfiles, "image");
        assertEquals("ready", imageWorkspace.get("status"));
        assertTrue(((List<?>) imageWorkspace.get("missingTools")).isEmpty());
        assertTrue(((List<?>) imageWorkspace.get("availableTools")).contains("image_generation"));

        Map<String, Object> mragWorkspace = workspace(workspaceProfiles, "mrag");
        assertEquals("ready", mragWorkspace.get("status"));
        assertTrue(((List<?>) mragWorkspace.get("missingTools")).isEmpty());
        assertTrue(((List<?>) mragWorkspace.get("availableTools")).containsAll(List.of(
                "multimodal_agent", "file_tool", "table_rag", "deep_search")));

        Map<String, Object> tradeWorkspace = workspace(workspaceProfiles, "trade");
        assertEquals("ready", tradeWorkspace.get("status"));
        assertTrue(((List<?>) tradeWorkspace.get("availableTools")).contains("trade_audit"));
    }

    @Test
    void shouldInjectEnabledAgentAdminConfigsIntoRuntimePrompt() {
        AgentAdminConfigHandler adminConfigHandler = new AgentAdminConfigHandler((Path) null);
        adminConfigHandler.upsertConfig(Map.of(
                "configId", "custom-trade-guard",
                "category", "system_prompt",
                "name", "Custom trade guard",
                "content", "Always audit quota settlement from backend facts.",
                "metadata", Map.of("workspace", "trade")));
        adminConfigHandler.upsertConfig(Map.of(
                "configId", "disabled-advisor",
                "category", "advisor",
                "name", "Disabled advisor",
                "content", "DO_NOT_INCLUDE",
                "enabled", false));

        BearDoctorNativeAgentService service = service(new AcademicToolCallbackFactory(
                new ObjectMapper(), null, null, null, null, null, null, null, null, null, null, null, null, null),
                adminConfigHandler);

        String tradePrompt = service.agentAdminRuntimePrompt("trade-audit");
        String imagePrompt = service.agentAdminRuntimePrompt("image");

        assertTrue(tradePrompt.contains("Agent 后台启用配置"));
        assertTrue(tradePrompt.contains("Always audit quota settlement from backend facts."));
        assertTrue(tradePrompt.contains("Use backend transaction data"));
        assertTrue(!tradePrompt.contains("DO_NOT_INCLUDE"));
        assertTrue(!imagePrompt.contains("Always audit quota settlement from backend facts."));
    }

    private static BearDoctorNativeAgentService service(AcademicToolCallbackFactory toolCallbackFactory) {
        return service(toolCallbackFactory, new AgentAdminConfigHandler((Path) null));
    }

    private static BearDoctorNativeAgentService service(AcademicToolCallbackFactory toolCallbackFactory,
                                                       AgentAdminConfigHandler agentAdminConfigHandler) {
        BearDoctorNativeAgentService service = new BearDoctorNativeAgentService(
                provider(null), null, null, null, null, null, null, null, null, null, null, toolCallbackFactory,
                agentAdminConfigHandler);
        ReflectionTestUtils.setField(service, "skillsDirectory", resolveProjectSkillsRoot().toString());
        return service;
    }

    private static Map<String, Object> workspace(List<Map<String, Object>> workspaceProfiles, String id) {
        return workspaceProfiles.stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> toolReadiness(List<Map<String, Object>> readiness, String name) {
        return readiness.stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private static Path resolveProjectSkillsRoot() {
        return List.of(
                        Path.of("..", "skills"),
                        Path.of("skills"),
                        Path.of("..", "..", "skills")
                ).stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("project skills directory not found"));
    }

    private static AcademicWebFetchPort webFetchPort() {
        return request -> new AcademicWebFetchPort.AcademicWebFetchResult(
                true,
                "Example Doc",
                request.url(),
                "remote page",
                "remote page",
                List.of(),
                Map.of("provider", "mock"),
                "");
    }

    private static AcademicCodeInterpreterPort codePort() {
        return request -> new AcademicCodeInterpreterPort.AcademicCodeExecutionResult(
                true, 0, "ok", "", "ok", request.code(), "done", List.of());
    }

    private static AcademicDataAnalysisPort dataAnalysisPort() {
        return request -> new AcademicDataAnalysisPort.AcademicDataAnalysisResult(
                true, "analysis done", "analysis done", List.of(), Map.of(), "");
    }

    private static AcademicReportPort reportPort() {
        return request -> new AcademicReportPort.AcademicReportResult(
                true, "report done", "report done", List.of(), Map.of(), "");
    }

    private static AcademicImageGenerationPort imagePort() {
        return request -> new AcademicImageGenerationPort.AcademicImageGenerationResult(
                true, "mock", "image generated", false, List.of(), "");
    }

    private static AcademicMultimodalAnalysisPort multimodalPort() {
        return request -> new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult(
                true, "analysis done", "analysis done", Map.of(), List.of(), "");
    }

    private static AcademicDeepSearchPort deepSearchPort() {
        return request -> new AcademicDeepSearchPort.AcademicDeepSearchResult(
                true, request.query(), "answer", "answer", List.of(), List.of(), List.of(), Map.of(), "");
    }

    private static AcademicTableRagPort tableRagPort() {
        return request -> new AcademicTableRagPort.AcademicTableRagResult(
                true,
                request.requestId(),
                List.of(new AcademicTableRagPort.AcademicTableSchemaMatch("trade_order", 0.9D, List.of())),
                Map.of(),
                "");
    }

    private static AcademicNl2SqlPort nl2SqlPort() {
        return request -> new AcademicNl2SqlPort.AcademicNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "think",
                "done",
                List.of(new AcademicNl2SqlPort.AcademicSqlCandidate(request.query(), "select 1")),
                Map.of(),
                "");
    }

    private static AcademicTradeAuditPort tradeAuditPort() {
        return request -> new AcademicTradeAuditPort.AcademicTradeAuditResult(
                true,
                "trade facts checked",
                Map.of("orderId", request.orderId()),
                List.of(Map.of("severity", "INFO", "code", "NO_BLOCKING_RISK")),
                Map.of(),
                "");
    }

    private static AcademicScriptRunnerPort scriptPort() {
        return request -> new AcademicScriptRunnerPort.AcademicScriptRunResult(
                true, 0, "ok", "", "script executed", List.of(), Map.of(), "");
    }

    private static AcademicFileToolPort fileToolPort() {
        return new AcademicFileToolPort() {
            @Override
            public AcademicFileToolResult upload(AcademicFileUploadRequest request) {
                return new AcademicFileToolResult(
                        true, "upload", request.fileName(), "", "uploaded", List.of(), Map.of(), "");
            }

            @Override
            public AcademicFileToolResult get(AcademicFileGetRequest request) {
                return new AcademicFileToolResult(
                        true, "get", request.fileName(), "content", "loaded", List.of(), Map.of(), "");
            }
        };
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
