package com.linrun.reactor.trigger.http.admin;

import com.linrun.reactor.api.response.Response;
import com.linrun.reactor.infrastructure.dao.IAiAgentDao;
import com.linrun.reactor.infrastructure.dao.IAiAgentFlowConfigDao;
import com.linrun.reactor.infrastructure.dao.IAiClientApiDao;
import com.linrun.reactor.infrastructure.dao.IAiClientDao;
import com.linrun.reactor.infrastructure.dao.IAiClientModelDao;
import com.linrun.reactor.infrastructure.dao.IAiClientSystemPromptDao;
import com.linrun.reactor.infrastructure.dao.IAiClientToolMcpDao;
import com.linrun.reactor.infrastructure.dao.po.AiAgent;
import com.linrun.reactor.infrastructure.dao.po.AiAgentFlowConfig;
import com.linrun.reactor.infrastructure.dao.po.AiClient;
import com.linrun.reactor.infrastructure.dao.po.AiClientApi;
import com.linrun.reactor.infrastructure.dao.po.AiClientModel;
import com.linrun.reactor.infrastructure.dao.po.AiClientSystemPrompt;
import com.linrun.reactor.infrastructure.dao.po.AiClientToolMcp;
import com.linrun.reactor.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/reactor")
public class ReactorAdminFacadeController {

    private static final String DEFAULT_API_ID = "default-openai-api";
    private static final String DEFAULT_CHAT_MODEL_ID = "default-chat-model";
    private static final String DEFAULT_IMAGE_MODEL_ID = "default-image-model";
    private static final String DEFAULT_EMBEDDING_MODEL_ID = "default-embedding-model";

    @Resource
    private IAiClientApiDao aiClientApiDao;
    @Resource
    private IAiClientModelDao aiClientModelDao;
    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;
    @Resource
    private IAiAgentDao aiAgentDao;
    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;
    @Resource
    private IAiClientDao aiClientDao;
    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;

    @GetMapping("/llm/config")
    public Response<Map<String, Object>> queryLlmConfig() {
        try {
            List<AiClientApi> apis = safeList(aiClientApiDao.queryAll());
            List<AiClientModel> models = safeList(aiClientModelDao.queryAll());
            AiClientApi defaultApi = firstEnabled(apis);
            AiClientModel chatModel = firstModel(models, "chat");
            AiClientModel embeddingModel = firstModel(models, "embedding");
            AiClientModel imageModel = firstModel(models, "image");

            Map<String, Object> chat = modelGroup(defaultApi, chatModel);
            Map<String, Object> embedding = modelGroup(defaultApi, embeddingModel);
            Map<String, Object> image = modelGroup(defaultApi, imageModel);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chat", chat);
            data.put("embedding", embedding);
            data.put("image", image);
            data.put("persisted", Map.of(
                    "chat", editModelGroup(defaultApi, chatModel),
                    "embedding", editModelGroup(defaultApi, embeddingModel),
                    "image", editModelGroup(defaultApi, imageModel)
            ));
            data.put("overrideFile", "Reactor ai_client_api / ai_client_model");
            return success(data);
        } catch (Exception e) {
            log.error("查询 Reactor 模型配置失败", e);
            return fail("查询 Reactor 模型配置失败");
        }
    }

    @PostMapping("/llm/config")
    public Response<Boolean> saveLlmConfig(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> chat = mapValue(payload, "chat");
            Map<String, Object> embedding = mapValue(payload, "embedding");
            Map<String, Object> image = mapValue(payload, "image");
            upsertApi(DEFAULT_API_ID, stringValue(chat, "baseUrl"), stringValue(chat, "apiKey"));
            upsertModel(DEFAULT_CHAT_MODEL_ID, DEFAULT_API_ID, stringValue(chat, "model"), "chat", "chat");
            upsertModel(DEFAULT_EMBEDDING_MODEL_ID, DEFAULT_API_ID, stringValue(embedding, "model"), "embedding", "embedding");
            upsertModel(DEFAULT_IMAGE_MODEL_ID, DEFAULT_API_ID, stringValue(image, "model"), "image", "image");
            return success(true);
        } catch (Exception e) {
            log.error("保存 Reactor 模型配置失败", e);
            return fail(false, "保存 Reactor 模型配置失败");
        }
    }

    @GetMapping("/skills")
    public Response<List<Map<String, Object>>> querySkills() {
        try {
            List<Map<String, Object>> skills = new ArrayList<>();
            for (AiAgent agent : safeList(aiAgentDao.queryAll())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", valueOr(agent.getAgentId(), agent.getAgentName()));
                item.put("description", valueOr(agent.getDescription(), agent.getStrategy()));
                item.put("enabled", isEnabled(agent.getStatus()));
                item.put("source", "reactor-agent");
                item.put("type", valueOr(agent.getStrategy(), agent.getChannel()));
                skills.add(item);
            }
            if (skills.isEmpty()) {
                skills.add(staticSkill("react", "ReAct 思考-行动循环", true));
                skills.add(staticSkill("plan-execute", "Plan-Execute 多步规划执行", true));
                skills.add(staticSkill("tool-runtime", "Python 工具运行时与产物登记", true));
                skills.add(staticSkill("hybrid-rag", "Qdrant 混合检索能力", true));
            }
            return success(skills);
        } catch (Exception e) {
            log.error("查询 Reactor 能力列表失败", e);
            return fail(List.of(), "查询 Reactor 能力列表失败");
        }
    }

    @PostMapping("/skills/{name}/enabled")
    public Response<Boolean> setSkillEnabled(@PathVariable("name") String name, @RequestBody Map<String, Object> payload) {
        try {
            AiAgent agent = aiAgentDao.queryByAgentId(name);
            if (agent == null) {
                return success(true);
            }
            agent.setStatus(booleanValue(payload, "enabled") ? 1 : 0);
            agent.setUpdateTime(LocalDateTime.now());
            return success(aiAgentDao.updateByAgentId(agent) > 0);
        } catch (Exception e) {
            log.error("切换 Reactor 能力状态失败, name={}", name, e);
            return fail(false, "切换 Reactor 能力状态失败");
        }
    }

    @GetMapping("/mcp/servers")
    public Response<List<Map<String, Object>>> queryMcpServers() {
        try {
            return success(safeList(aiClientToolMcpDao.queryAll()).stream()
                    .map(this::mcpServer)
                    .toList());
        } catch (Exception e) {
            log.error("查询 Reactor MCP 服务失败", e);
            return fail(List.of(), "查询 Reactor MCP 服务失败");
        }
    }

    @PostMapping("/mcp/servers")
    public Response<Boolean> registerMcpServer(@RequestBody Map<String, Object> payload) {
        try {
            String serverId = firstText(payload, "serverId", "mcpId");
            if (!StringUtils.hasText(serverId)) {
                return fail(false, "MCP 服务编号不能为空");
            }
            AiClientToolMcp mcp = Optional.ofNullable(aiClientToolMcpDao.queryByMcpId(serverId))
                    .orElseGet(AiClientToolMcp::new);
            boolean insert = mcp.getId() == null;
            mcp.setMcpId(serverId);
            mcp.setMcpName(valueOr(firstText(payload, "name", "mcpName"), serverId));
            mcp.setTransportType(valueOr(firstText(payload, "transport", "transportType"), "streamable_http"));
            mcp.setTransportConfig(firstText(payload, "endpoint", "transportConfig"));
            mcp.setRequestTimeout(intValue(payload, "requestTimeout", 10));
            mcp.setStatus(booleanValue(payload, "enabled", true) ? 1 : 0);
            if (insert) {
                mcp.setCreateTime(LocalDateTime.now());
            }
            mcp.setUpdateTime(LocalDateTime.now());
            int affected = insert ? aiClientToolMcpDao.insert(mcp) : aiClientToolMcpDao.updateByMcpId(mcp);
            return success(affected > 0);
        } catch (Exception e) {
            log.error("保存 Reactor MCP 服务失败", e);
            return fail(false, "保存 Reactor MCP 服务失败");
        }
    }

    @PostMapping("/mcp/servers/{serverId}/enabled")
    public Response<Boolean> enableMcpServer(@PathVariable("serverId") String serverId, @RequestBody Map<String, Object> payload) {
        try {
            AiClientToolMcp mcp = aiClientToolMcpDao.queryByMcpId(serverId);
            if (mcp == null) {
                return fail(false, "MCP 服务不存在");
            }
            mcp.setStatus(booleanValue(payload, "enabled") ? 1 : 0);
            mcp.setUpdateTime(LocalDateTime.now());
            return success(aiClientToolMcpDao.updateByMcpId(mcp) > 0);
        } catch (Exception e) {
            log.error("切换 Reactor MCP 服务失败, serverId={}", serverId, e);
            return fail(false, "切换 Reactor MCP 服务失败");
        }
    }

    @PostMapping("/mcp/servers/{serverId}/tools/cache")
    public Response<Map<String, Object>> cacheMcpTools(@PathVariable("serverId") String serverId, @RequestBody Map<String, Object> payload) {
        return success(Map.of("serverId", serverId, "cached", true, "tools", listValue(payload, "tools")));
    }

    @PostMapping("/mcp/servers/{serverId}/tools/discover")
    public Response<Map<String, Object>> discoverMcpTools(@PathVariable("serverId") String serverId, @RequestBody Map<String, Object> payload) {
        return success(Map.of("serverId", serverId, "cache", booleanValue(payload, "cache", true), "tools", List.of()));
    }

    @GetMapping("/mcp/tools")
    public Response<List<Map<String, Object>>> queryMcpTools(@RequestParam(value = "serverId", required = false) String serverId,
                                                             @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly) {
        try {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (AiClientToolMcp mcp : safeList(aiClientToolMcpDao.queryAll())) {
                if (StringUtils.hasText(serverId) && !Objects.equals(serverId, mcp.getMcpId())) {
                    continue;
                }
                if (enabledOnly && !isEnabled(mcp.getStatus())) {
                    continue;
                }
                tools.add(Map.of(
                        "serverId", mcp.getMcpId(),
                        "name", mcp.getMcpId() + ".configured",
                        "description", "Reactor MCP 服务配置已登记，工具发现由运行时加载"
                ));
            }
            return success(tools);
        } catch (Exception e) {
            log.error("查询 Reactor MCP 工具失败", e);
            return fail(List.of(), "查询 Reactor MCP 工具失败");
        }
    }

    @GetMapping("/mcp/health")
    public Response<Map<String, Object>> queryMcpHealth() {
        try {
            long enabledCount = safeList(aiClientToolMcpDao.queryAll()).stream()
                    .filter(item -> isEnabled(item.getStatus()))
                    .count();
            return success(Map.of("status", "UP", "runtime", "reactor", "enabledServerCount", enabledCount));
        } catch (Exception e) {
            log.error("查询 Reactor MCP 健康状态失败", e);
            return fail(Map.of("status", "DOWN"), "查询 Reactor MCP 健康状态失败");
        }
    }

    @GetMapping("/mcp/export")
    public Response<Map<String, Object>> exportMcpState() {
        try {
            return success(Map.of("servers", safeList(aiClientToolMcpDao.queryAll()).stream()
                    .map(this::mcpServer)
                    .toList()));
        } catch (Exception e) {
            log.error("导出 Reactor MCP 配置失败", e);
            return fail(Map.of("servers", List.of()), "导出 Reactor MCP 配置失败");
        }
    }

    @PostMapping("/mcp/import")
    public Response<Map<String, Object>> importMcpState(@RequestBody Map<String, Object> payload) {
        Object snapshot = payload.get("snapshot");
        if (snapshot instanceof Map<?, ?> map && map.get("servers") instanceof List<?> servers) {
            for (Object item : servers) {
                if (item instanceof Map<?, ?> raw) {
                    registerMcpServer(toStringObjectMap(raw));
                }
            }
            return success(Map.of("imported", servers.size()));
        }
        return success(Map.of("imported", 0));
    }

    @PostMapping("/mcp/tools/{toolName}/call")
    public Response<Map<String, Object>> callMcpTool(@PathVariable("toolName") String toolName, @RequestBody Map<String, Object> payload) {
        return success(Map.of(
                "toolName", toolName,
                "called", false,
                "message", "Reactor MCP 工具调用由 Agent 运行时触发，管理端仅登记配置",
                "arguments", Optional.ofNullable(payload.get("arguments")).orElse(Map.of())
        ));
    }

    @GetMapping("/configs")
    public Response<List<Map<String, Object>>> queryConfigs(@RequestParam(value = "category", required = false) String category,
                                                            @RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly) {
        try {
            List<Map<String, Object>> configs = new ArrayList<>();
            safeList(aiClientModelDao.queryAll()).forEach(item -> configs.add(modelConfig(item)));
            safeList(aiClientApiDao.queryAll()).forEach(item -> configs.add(apiConfig(item)));
            safeList(aiClientSystemPromptDao.queryAll()).forEach(item -> configs.add(promptConfig(item)));
            safeList(aiClientToolMcpDao.queryAll()).forEach(item -> configs.add(mcpConfig(item)));
            return success(configs.stream()
                    .filter(item -> !StringUtils.hasText(category) || Objects.equals(category, item.get("category")))
                    .filter(item -> !enabledOnly || Boolean.TRUE.equals(item.get("enabled")))
                    .toList());
        } catch (Exception e) {
            log.error("查询 Reactor 管理配置失败", e);
            return fail(List.of(), "查询 Reactor 管理配置失败");
        }
    }

    @PostMapping("/configs")
    public Response<Boolean> upsertConfig(@RequestBody Map<String, Object> payload) {
        String category = stringValue(payload, "category").toLowerCase(Locale.ROOT);
        if ("mcp".equals(category)) {
            return registerMcpServer(payload);
        }
        if ("model".equals(category)) {
            String modelId = valueOr(stringValue(payload, "configId"), stringValue(payload, "modelId"));
            String model = valueOr(stringValue(payload, "model"), stringValue(payload, "name"));
            upsertModel(modelId, DEFAULT_API_ID, model, "chat", "chat");
            return success(true);
        }
        return success(true);
    }

    @PostMapping("/configs/{configId}/enabled")
    public Response<Boolean> enableConfig(@PathVariable("configId") String configId, @RequestBody Map<String, Object> payload) {
        boolean enabled = booleanValue(payload, "enabled");
        AiClientModel model = aiClientModelDao.queryByModelId(configId);
        if (model != null) {
            model.setStatus(enabled ? 1 : 0);
            model.setUpdateTime(LocalDateTime.now());
            return success(aiClientModelDao.updateByModelId(model) > 0);
        }
        AiClientToolMcp mcp = aiClientToolMcpDao.queryByMcpId(configId);
        if (mcp != null) {
            mcp.setStatus(enabled ? 1 : 0);
            mcp.setUpdateTime(LocalDateTime.now());
            return success(aiClientToolMcpDao.updateByMcpId(mcp) > 0);
        }
        return success(true);
    }

    @DeleteMapping("/configs/{configId}")
    public Response<Boolean> deleteConfig(@PathVariable("configId") String configId) {
        if (aiClientModelDao.queryByModelId(configId) != null) {
            return success(aiClientModelDao.deleteByModelId(configId) > 0);
        }
        if (aiClientToolMcpDao.queryByMcpId(configId) != null) {
            return success(aiClientToolMcpDao.deleteByMcpId(configId) > 0);
        }
        return success(true);
    }

    @GetMapping("/export")
    public Response<Map<String, Object>> exportState() {
        return success(Map.of(
                "llm", queryLlmConfig().getData(),
                "mcp", exportMcpState().getData(),
                "configs", queryConfigs("", false).getData()
        ));
    }

    @PostMapping("/import")
    public Response<Map<String, Object>> importState(@RequestBody Map<String, Object> payload) {
        if (payload.get("mcp") instanceof Map<?, ?> rawMcp) {
            importMcpState(toStringObjectMap(rawMcp));
        }
        return success(Map.of("imported", true));
    }

    @GetMapping("/statistics")
    public Response<Map<String, Object>> queryStatistics() {
        try {
            return success(Map.of(
                    "agentCount", safeList(aiAgentDao.queryAll()).size(),
                    "clientCount", safeList(aiClientDao.queryAll()).size(),
                    "modelCount", safeList(aiClientModelDao.queryAll()).size(),
                    "mcpServerCount", safeList(aiClientToolMcpDao.queryAll()).size(),
                    "systemPromptCount", safeList(aiClientSystemPromptDao.queryAll()).size()
            ));
        } catch (Exception e) {
            log.error("查询 Reactor 统计失败", e);
            return fail(Map.of(), "查询 Reactor 统计失败");
        }
    }

    @GetMapping("/runtime-snapshot")
    public Response<Map<String, Object>> queryRuntimeSnapshot() {
        try {
            List<AiAgent> agents = safeList(aiAgentDao.queryAll());
            List<AiAgentFlowConfig> flowConfigs = safeList(aiAgentFlowConfigDao.queryAll());
            return success(Map.of(
                    "runtime", "reactor-agent",
                    "entrypoint", "/web/api/v1/gpt/queryAgentStreamIncr",
                    "capabilities", List.of("ReAct", "Plan-Execute", "Python Tool Runtime", "Hybrid RAG", "Execution Ledger"),
                    "enabledAgents", agents.stream().filter(item -> isEnabled(item.getStatus())).map(AiAgent::getAgentId).toList(),
                    "flowStepCount", flowConfigs.size(),
                    "mcpEnabledServerCount", safeList(aiClientToolMcpDao.queryAll()).stream().filter(item -> isEnabled(item.getStatus())).count()
            ));
        } catch (Exception e) {
            log.error("查询 Reactor 运行快照失败", e);
            return fail(Map.of(), "查询 Reactor 运行快照失败");
        }
    }

    private void upsertApi(String apiId, String baseUrl, String apiKey) {
        if (!StringUtils.hasText(baseUrl) && !StringUtils.hasText(apiKey)) {
            return;
        }
        AiClientApi api = Optional.ofNullable(aiClientApiDao.queryByApiId(apiId)).orElseGet(AiClientApi::new);
        boolean insert = api.getId() == null;
        api.setApiId(apiId);
        if (StringUtils.hasText(baseUrl)) api.setBaseUrl(baseUrl);
        if (StringUtils.hasText(apiKey)) api.setApiKey(apiKey);
        api.setCompletionsPath(valueOr(api.getCompletionsPath(), "/v1/chat/completions"));
        api.setEmbeddingsPath(valueOr(api.getEmbeddingsPath(), "/v1/embeddings"));
        api.setStatus(1);
        if (insert) api.setCreateTime(LocalDateTime.now());
        api.setUpdateTime(LocalDateTime.now());
        if (insert) {
            aiClientApiDao.insert(api);
        } else {
            aiClientApiDao.updateByApiId(api);
        }
    }

    private void upsertModel(String modelId, String apiId, String modelName, String modelType, String modelUsage) {
        if (!StringUtils.hasText(modelId) || !StringUtils.hasText(modelName)) {
            return;
        }
        AiClientModel model = Optional.ofNullable(aiClientModelDao.queryByModelId(modelId)).orElseGet(AiClientModel::new);
        boolean insert = model.getId() == null;
        model.setModelId(modelId);
        model.setApiId(apiId);
        model.setModelName(modelName);
        model.setModelType(modelType);
        model.setModelUsage(modelUsage);
        model.setStatus(1);
        if (insert) model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        if (insert) {
            aiClientModelDao.insert(model);
        } else {
            aiClientModelDao.updateByModelId(model);
        }
    }

    private Map<String, Object> modelGroup(AiClientApi api, AiClientModel model) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiKey", mask(api == null ? "" : api.getApiKey()));
        data.put("baseUrl", api == null ? "" : valueOr(api.getBaseUrl(), ""));
        data.put("model", model == null ? "" : valueOr(model.getModelName(), ""));
        return data;
    }

    private Map<String, Object> editModelGroup(AiClientApi api, AiClientModel model) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiKey", "");
        data.put("baseUrl", api == null ? "" : valueOr(api.getBaseUrl(), ""));
        data.put("model", model == null ? "" : valueOr(model.getModelName(), ""));
        return data;
    }

    private AiClientApi firstEnabled(List<AiClientApi> apis) {
        return apis.stream().filter(item -> isEnabled(item.getStatus())).findFirst()
                .orElse(apis.isEmpty() ? null : apis.getFirst());
    }

    private AiClientModel firstModel(List<AiClientModel> models, String usage) {
        return models.stream()
                .filter(item -> containsIgnoreCase(item.getModelUsage(), usage) || containsIgnoreCase(item.getModelType(), usage))
                .filter(item -> isEnabled(item.getStatus()))
                .findFirst()
                .orElseGet(() -> models.stream()
                        .filter(item -> containsIgnoreCase(item.getModelUsage(), usage) || containsIgnoreCase(item.getModelType(), usage))
                        .findFirst()
                        .orElse(null));
    }

    private Map<String, Object> mcpServer(AiClientToolMcp mcp) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverId", mcp.getMcpId());
        data.put("name", valueOr(mcp.getMcpName(), mcp.getMcpId()));
        data.put("endpoint", valueOr(mcp.getTransportConfig(), ""));
        data.put("transport", valueOr(mcp.getTransportType(), ""));
        data.put("toolCount", 0);
        data.put("enabled", isEnabled(mcp.getStatus()));
        data.put("requestTimeout", mcp.getRequestTimeout());
        return data;
    }

    private Map<String, Object> modelConfig(AiClientModel model) {
        return config(model.getModelId(), "model", model.getModelName(), model.getModelUsage(), isEnabled(model.getStatus()));
    }

    private Map<String, Object> apiConfig(AiClientApi api) {
        return config(api.getApiId(), "api", api.getBaseUrl(), api.getCompletionsPath(), isEnabled(api.getStatus()));
    }

    private Map<String, Object> promptConfig(AiClientSystemPrompt prompt) {
        return config(prompt.getPromptId(), "system_prompt", prompt.getPromptName(), prompt.getDescription(), isEnabled(prompt.getStatus()));
    }

    private Map<String, Object> mcpConfig(AiClientToolMcp mcp) {
        return config(mcp.getMcpId(), "mcp", mcp.getMcpName(), mcp.getTransportConfig(), isEnabled(mcp.getStatus()));
    }

    private Map<String, Object> config(String id, String category, String name, String description, boolean enabled) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configId", id);
        data.put("category", category);
        data.put("name", valueOr(name, id));
        data.put("description", valueOr(description, ""));
        data.put("enabled", enabled);
        data.put("content", "");
        data.put("metadata", Map.of("runtime", "reactor"));
        return data;
    }

    private Map<String, Object> staticSkill(String name, String description, boolean enabled) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", description);
        item.put("enabled", enabled);
        item.put("source", "reactor-runtime");
        return item;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private boolean containsIgnoreCase(String text, String expected) {
        return StringUtils.hasText(text) && text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean isEnabled(Integer status) {
        return status == null || status == 1;
    }

    private String firstText(Map<String, Object> map, String firstKey, String secondKey) {
        String first = stringValue(map, firstKey);
        return StringUtils.hasText(first) ? first : stringValue(map, secondKey);
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Map<?, ?> raw ? toStringObjectMap(raw) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private boolean booleanValue(Map<String, Object> map, String key) {
        return booleanValue(map, key, false);
    }

    private boolean booleanValue(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> fail(String message) {
        return fail(null, message);
    }

    private <T> Response<T> fail(T data, String message) {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .data(data)
                .build();
    }
}
