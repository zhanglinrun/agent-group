package com.linrun.trigger.http;

import com.linrun.trigger.config.AgentAdminConfigProperties;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAdminConfigHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadDefaultAgentAdminCategories() {
        Path stateFile = tempDir.resolve("agent-admin-state.json");

        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(stateFile);

        List<String> categories = handler.listConfigs("", false).stream()
                .map(item -> String.valueOf(item.get("category")))
                .toList();
        assertTrue(categories.containsAll(List.of(
                "agent_client",
                "model",
                "api",
                "system_prompt",
                "advisor",
                "rag_order",
                "tool",
                "mcp_tool",
                "draw_config")));
        assertTrue(Files.isRegularFile(stateFile));
    }

    @Test
    void shouldUpsertToggleExportImportAndReloadState() {
        Path stateFile = tempDir.resolve("agent-admin-state.json");
        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(stateFile);

        Map<String, Object> saved = handler.upsertConfig(Map.of(
                "configId", "Custom Model",
                "category", "model",
                "name", "Custom model",
                "content", "qwen-custom",
                "orderNo", 1,
                "metadata", Map.of("provider", "test")));

        assertEquals("custom-model", saved.get("configId"));
        assertEquals("model", saved.get("category"));
        assertEquals("qwen-custom", handler.getConfig("custom-model").get("content"));

        handler.enableConfig("custom-model", false);
        assertEquals(false, handler.getConfig("custom-model").get("enabled"));

        Map<String, Object> exported = handler.exportState();
        assertTrue((Integer) exported.get("configCount") >= 9);

        AgentAdminConfigHandler reloaded = new AgentAdminConfigHandler(stateFile);
        assertEquals(false, reloaded.getConfig("custom-model").get("enabled"));
        assertEquals("test", ((Map<?, ?>) reloaded.getConfig("custom-model").get("metadata")).get("provider"));

        Map<String, Object> imported = reloaded.importState(Map.of(
                "replace", true,
                "configs", List.of(Map.of(
                        "configId", "trade-system-prompt",
                        "category", "system_prompt",
                        "name", "Trade system prompt",
                        "content", "Use backend trade state only.",
                        "enabled", true))));

        assertEquals(1, imported.get("imported"));
        assertEquals(1, imported.get("configCount"));
        assertEquals("system_prompt", reloaded.getConfig("trade-system-prompt").get("category"));
        assertThrows(AppException.class, () -> reloaded.getConfig("custom-model"));
    }

    @Test
    void shouldExposeStatistics() {
        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(tempDir.resolve("stats.json"));

        Map<String, Object> statistics = handler.statistics();

        assertTrue((Integer) statistics.get("configCount") >= 9);
        assertTrue((Integer) statistics.get("enabledCount") >= 9);
        assertTrue((Integer) statistics.get("categoryCount") >= 9);
        assertTrue(((Map<?, ?>) statistics.get("categoryCounts")).containsKey("agent_client"));
        assertTrue(((List<?>) statistics.get("adminEndpoints")).contains("/api/v1/agent/admin/configs"));
        assertTrue(((List<?>) statistics.get("adminEndpoints")).contains("/api/v1/agent/admin/runtime-snapshot"));
        assertTrue(((List<?>) statistics.get("adminEndpoints")).contains("/api/v1/agent/admin/assembly"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeRuntimeSnapshotAndMaskSensitiveValues() {
        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(tempDir.resolve("runtime.json"));
        handler.upsertConfig(Map.of(
                "configId", "secret-api",
                "category", "api",
                "name", "Secret API",
                "content", "sk-secretkey123456",
                "metadata", Map.of(
                        "apiKey", "sk-secretkey123456",
                        "nested", Map.of("password", "secret-password"))));

        Map<String, Object> snapshot = handler.runtimeSnapshot();
        Map<String, Object> sections = (Map<String, Object>) snapshot.get("runtimeSections");
        Map<String, Object> policies = (Map<String, Object>) snapshot.get("runtimePolicies");
        Map<String, Object> codePolicy = (Map<String, Object>) policies.get("codeInterpreter");
        List<Map<String, Object>> assemblyPlan = (List<Map<String, Object>>) snapshot.get("assemblyPlan");
        String snapshotText = snapshot.toString();

        assertEquals("agent-admin-runtime", snapshot.get("snapshotType"));
        assertEquals(true, snapshot.get("sensitiveMasked"));
        assertTrue(((Map<?, ?>) snapshot.get("activeCategoryCounts")).containsKey("api"));
        assertTrue(((List<?>) sections.get("apis")).size() >= 1);
        assertTrue(((List<?>) sections.get("tools")).size() >= 1);
        assertTrue(((List<?>) sections.get("mcpTools")).size() >= 1);
        assertEquals("agent_client", assemblyPlan.getFirst().get("stageKey"));
        assertTrue(assemblyPlan.stream().anyMatch(item -> "mcp_tool".equals(item.get("stageKey"))));
        assertEquals("analysis", codePolicy.get("defaultPermissionProfile"));
        assertTrue(((List<?>) codePolicy.get("allowedPermissionProfiles")).contains("workspace"));
        assertTrue(snapshotText.contains("******"));
        assertTrue(!snapshotText.contains("secretkey123456"));
        assertTrue(!snapshotText.contains("secret-password"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeRuntimeAssemblyPlan() {
        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(tempDir.resolve("assembly.json"));

        Map<String, Object> assembly = handler.runtimeAssembly();
        List<Map<String, Object>> plan = (List<Map<String, Object>>) assembly.get("assemblyPlan");

        assertEquals("agent-client-runtime-assembly", assembly.get("assemblyType"));
        assertEquals(9, assembly.get("stageCount"));
        assertEquals("agent_client", plan.getFirst().get("stageKey"));
        assertEquals("mcp_tool", plan.get(7).get("stageKey"));
        assertEquals(true, assembly.get("sensitiveMasked"));
    }

    @Test
    void shouldImportConfiguredState() {
        AgentAdminConfigProperties properties = new AgentAdminConfigProperties();
        properties.setStateFile(tempDir.resolve("configured-agent-admin-state.json").toString());
        AgentAdminConfigProperties.Config config = new AgentAdminConfigProperties.Config();
        config.setConfigId("startup-advisor");
        config.setCategory("advisor");
        config.setName("Startup advisor");
        config.setContent("attach memory and RAG");
        config.setOrderNo(7);
        config.setMetadata(Map.of("type", "startup"));
        properties.setConfigs(List.of(config));

        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(null, properties);

        assertEquals("advisor", handler.getConfig("startup-advisor").get("category"));
        assertEquals(1, handler.listConfigs("", false).size());
        assertTrue(Files.isRegularFile(tempDir.resolve("configured-agent-admin-state.json")));
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        AgentAdminConfigHandler handler = new AgentAdminConfigHandler(tempDir.resolve("invalid.json"));

        AppException missingConfigId = assertThrows(AppException.class,
                () -> handler.upsertConfig(Map.of("category", "model")));
        AppException missingCategory = assertThrows(AppException.class,
                () -> handler.upsertConfig(Map.of("configId", "model")));

        assertEquals("AGENT_ADMIN_0001", missingConfigId.getCode());
        assertEquals("AGENT_ADMIN_0002", missingCategory.getCode());
    }
}
