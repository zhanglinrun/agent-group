package com.linrun.trigger.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentAdminConfigControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldListUpsertToggleExportImportAndDeleteConfigs() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentAdminConfigController(
                        new AgentAdminConfigHandler(tempDir.resolve("agent-admin-state.json"))))
                .build();

        mockMvc.perform(get("/api/v1/agent/admin/configs?category=model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].category").value("model"));

        mockMvc.perform(post("/api/v1/agent/admin/configs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "configId": "custom-model",
                                  "category": "model",
                                  "name": "Custom model",
                                  "content": "qwen-custom",
                                  "metadata": {
                                    "provider": "test"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configId").value("custom-model"))
                .andExpect(jsonPath("$.data.metadata.provider").value("test"));

        mockMvc.perform(post("/api/v1/agent/admin/configs/custom-model/enabled")
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/v1/agent/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminEndpoints[0]").value("/api/v1/agent/admin/configs"));

        mockMvc.perform(get("/api/v1/agent/admin/runtime-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotType").value("agent-admin-runtime"))
                .andExpect(jsonPath("$.data.sensitiveMasked").value(true));

        mockMvc.perform(get("/api/v1/agent/admin/assembly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assemblyType").value("agent-client-runtime-assembly"))
                .andExpect(jsonPath("$.data.assemblyPlan[0].stageKey").value("agent_client"));

        mockMvc.perform(get("/api/v1/agent/admin/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configCount").isNumber());

        mockMvc.perform(post("/api/v1/agent/admin/import")
                        .contentType("application/json")
                        .content("""
                                {
                                  "replace": true,
                                  "configs": [
                                    {
                                      "configId": "trade-prompt",
                                      "category": "system_prompt",
                                      "name": "Trade prompt",
                                      "content": "Use backend trade state."
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.configCount").value(1));

        mockMvc.perform(delete("/api/v1/agent/admin/configs/trade-prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configId").value("trade-prompt"));
    }
}
