package com.linrun.trigger.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.trigger.config.AgentAdminConfigProperties;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AgentAdminConfigHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ConcurrentMap<String, Map<String, Object>> configs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path stateFile;

    public AgentAdminConfigHandler() {
        this(new ObjectMapper().findAndRegisterModules(), (AgentAdminConfigProperties) null);
    }

    @Autowired
    public AgentAdminConfigHandler(ObjectMapper objectMapper, AgentAdminConfigProperties properties) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.stateFile = resolveStateFile(properties == null ? "" : properties.getStateFile());
        loadState();
        importConfiguredState(properties);
        loadDefaultsIfEmpty();
    }

    AgentAdminConfigHandler(Path stateFile) {
        this(new ObjectMapper().findAndRegisterModules(), stateFile);
    }

    AgentAdminConfigHandler(ObjectMapper objectMapper, Path stateFile) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.stateFile = stateFile == null ? null : stateFile.toAbsolutePath().normalize();
        loadState();
        loadDefaultsIfEmpty();
    }

    public List<Map<String, Object>> listConfigs(String category, boolean enabledOnly) {
        String safeCategory = normalizeCategory(category);
        return configs.values().stream()
                .filter(item -> !StringUtils.hasText(safeCategory)
                        || safeCategory.equals(String.valueOf(item.getOrDefault("category", ""))))
                .filter(item -> !enabledOnly || bool(item.get("enabled"), true))
                .<Map<String, Object>>map(LinkedHashMap::new)
                .sorted(Comparator
                        .comparingInt((Map<String, Object> item) -> number(item.get("orderNo"), 0))
                        .thenComparing(item -> String.valueOf(item.getOrDefault("configId", ""))))
                .toList();
    }

    public Map<String, Object> getConfig(String configId) {
        Map<String, Object> item = configs.get(normalizeId(configId));
        if (item == null) {
            throw new AppException("AGENT_ADMIN_0404", "agent config not found: " + configId);
        }
        return new LinkedHashMap<>(item);
    }

    public Map<String, Object> upsertConfig(Map<String, Object> request) {
        return upsertConfig(request, true);
    }

    private Map<String, Object> upsertConfig(Map<String, Object> request, boolean persist) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String configId = normalizeId(text(body.get("configId")));
        String category = normalizeCategory(text(body.get("category")));
        if (!StringUtils.hasText(configId)) {
            throw new AppException("AGENT_ADMIN_0001", "configId is required");
        }
        if (!StringUtils.hasText(category)) {
            throw new AppException("AGENT_ADMIN_0002", "category is required");
        }
        Map<String, Object> previous = configs.get(configId);
        String now = LocalDateTime.now().toString();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("configId", configId);
        item.put("category", category);
        item.put("name", defaultText(body.get("name"), configId));
        item.put("description", text(body.get("description")));
        item.put("content", text(body.get("content")));
        item.put("enabled", bool(body.get("enabled"), previous == null || bool(previous.get("enabled"), true)));
        item.put("orderNo", number(body.get("orderNo"), previous == null ? 0 : number(previous.get("orderNo"), 0)));
        item.put("metadata", map(body.get("metadata")));
        item.put("createdAt", previous == null ? now : String.valueOf(previous.getOrDefault("createdAt", now)));
        item.put("updatedAt", now);
        configs.put(configId, item);
        if (persist) {
            persistState();
        }
        return new LinkedHashMap<>(item);
    }

    public Map<String, Object> enableConfig(String configId, boolean enabled) {
        String safeConfigId = normalizeId(configId);
        Map<String, Object> previous = configs.get(safeConfigId);
        if (previous == null) {
            throw new AppException("AGENT_ADMIN_0404", "agent config not found: " + configId);
        }
        Map<String, Object> item = new LinkedHashMap<>(previous);
        item.put("enabled", enabled);
        item.put("updatedAt", LocalDateTime.now().toString());
        configs.put(safeConfigId, item);
        persistState();
        return new LinkedHashMap<>(item);
    }

    public Map<String, Object> deleteConfig(String configId) {
        Map<String, Object> removed = configs.remove(normalizeId(configId));
        if (removed == null) {
            throw new AppException("AGENT_ADMIN_0404", "agent config not found: " + configId);
        }
        persistState();
        return new LinkedHashMap<>(removed);
    }

    public Map<String, Object> exportState() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configCount", configs.size());
        result.put("categories", categories());
        result.put("configs", listConfigs("", false));
        return result;
    }

    public Map<String, Object> statistics() {
        List<Map<String, Object>> allConfigs = listConfigs("", false);
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        int enabledCount = 0;
        for (Map<String, Object> item : allConfigs) {
            String category = String.valueOf(item.getOrDefault("category", ""));
            categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            if (bool(item.get("enabled"), true)) {
                enabledCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configCount", allConfigs.size());
        result.put("enabledCount", enabledCount);
        result.put("disabledCount", allConfigs.size() - enabledCount);
        result.put("categoryCount", categoryCounts.size());
        result.put("categories", categories());
        result.put("categoryCounts", categoryCounts);
        result.put("stateFile", stateFile == null ? "" : stateFile.toString());
        result.put("adminEndpoints", List.of(
                "/api/v1/agent/admin/configs",
                "/api/v1/agent/admin/export",
                "/api/v1/agent/admin/import",
                "/api/v1/agent/admin/statistics",
                "/api/v1/agent/admin/runtime-snapshot"));
        return result;
    }

    public Map<String, Object> runtimeSnapshot() {
        List<Map<String, Object>> allConfigs = listConfigs("", false);
        List<Map<String, Object>> enabledConfigs = allConfigs.stream()
                .filter(item -> bool(item.get("enabled"), true))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotType", "agent-admin-runtime");
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("stateFile", stateFile == null ? "" : stateFile.toString());
        result.put("configCount", allConfigs.size());
        result.put("enabledCount", enabledConfigs.size());
        result.put("disabledCount", allConfigs.size() - enabledConfigs.size());
        result.put("categories", categories());
        result.put("activeCategoryCounts", categoryCounts(enabledConfigs));
        result.put("runtimeSections", runtimeSections(enabledConfigs));
        result.put("runtimePolicies", runtimePolicies());
        result.put("enabledConfigs", enabledConfigs.stream()
                .map(this::runtimeConfig)
                .toList());
        result.put("sensitiveMasked", true);
        result.put("notes", List.of(
                "Only enabled configs are applied to runtime prompts.",
                "Secrets and credential-like fields are masked in this snapshot.",
                "Quota, order, payment and group settlement facts still come from backend transaction services."));
        return result;
    }

    public Map<String, Object> importState(Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        boolean replace = bool(body.get("replace"), false);
        List<Map<String, Object>> incoming = list(body.get("configs")).stream()
                .filter(Map.class::isInstance)
                .map(item -> map(item))
                .toList();
        if (replace) {
            configs.clear();
        }
        List<Map<String, Object>> saved = new ArrayList<>();
        for (Map<String, Object> item : incoming) {
            saved.add(upsertConfig(item));
        }
        persistState();
        return Map.of(
                "imported", saved.size(),
                "replaced", replace,
                "configCount", configs.size(),
                "configs", saved);
    }

    private List<Map<String, Object>> categories() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : listConfigs("", false)) {
            String category = String.valueOf(item.getOrDefault("category", ""));
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();
    }

    private void importConfiguredState(AgentAdminConfigProperties properties) {
        if (properties == null || properties.getConfigs().isEmpty()) {
            return;
        }
        for (AgentAdminConfigProperties.Config config : properties.getConfigs()) {
            if (config == null || !StringUtils.hasText(config.getConfigId())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configId", config.getConfigId());
            item.put("category", config.getCategory());
            item.put("name", config.getName());
            item.put("description", config.getDescription());
            item.put("content", config.getContent());
            item.put("enabled", config.isEnabled());
            item.put("orderNo", config.getOrderNo());
            item.put("metadata", config.getMetadata());
            upsertConfig(item, properties.isPersistImportedState());
        }
        if (properties.isPersistImportedState()) {
            persistState();
        }
    }

    private void loadDefaultsIfEmpty() {
        if (!configs.isEmpty()) {
            return;
        }
        List.of(
                defaultConfig("default-agent-client", "agent_client", "Default agent client",
                        "Default agent client profile used by workspace and trade aware agent flows.",
                        "agent_workspace -> tool_runtime -> trade_quota_guard", 5, Map.of("runtime", "spring-ai")),
                defaultConfig("default-model", "model", "Default chat model",
                        "Default Spring AI chat model used by the agent runtime.",
                        "qwen3.6-plus", 10, Map.of("provider", "spring-ai-openai-compatible")),
                defaultConfig("default-api", "api", "OpenAI compatible API",
                        "Default OpenAI compatible API endpoint template.",
                        "${OPENAI_BASE_URL}", 20, Map.of("auth", "environment-variable")),
                defaultConfig("trade-guard-system-prompt", "system_prompt", "Trade guard system prompt",
                        "High risk facts such as quota, order, payment and group status must come from backend services.",
                        "Use backend transaction data for quota, order, payment and group settlement facts.", 30, Map.of("scene", "trade-quota")),
                defaultConfig("quota-rag-advisor", "advisor", "Quota RAG advisor",
                        "Advisor preset for quota package and trade knowledge retrieval.",
                        "Attach quota package knowledge, order status tools and payment state checks before final answer.", 40, Map.of("type", "rag")),
                defaultConfig("knowledge-rag-order", "rag_order", "Knowledge recall order",
                        "Default recall order for agent answers.",
                        "query_rewrite -> vector_recall -> rerank -> answer_reflection", 50, Map.of("vectorStore", "pgvector")),
                defaultConfig("image-draw-config", "draw_config", "Image generation config",
                        "Default image generation workspace config.",
                        "image_generation -> artifact_registry -> quota_consume", 60, Map.of("workspace", "image"))
        ).forEach(item -> configs.put(String.valueOf(item.get("configId")), item));
        persistState();
    }

    private Map<String, Object> defaultConfig(String configId,
                                              String category,
                                              String name,
                                              String description,
                                              String content,
                                              int orderNo,
                                              Map<String, Object> metadata) {
        String now = LocalDateTime.now().toString();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("configId", configId);
        item.put("category", category);
        item.put("name", name);
        item.put("description", description);
        item.put("content", content);
        item.put("enabled", true);
        item.put("orderNo", orderNo);
        item.put("metadata", metadata == null ? Map.of() : metadata);
        item.put("createdAt", now);
        item.put("updatedAt", now);
        return item;
    }

    private void loadState() {
        if (stateFile == null || !Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            Map<String, Object> state = objectMapper.readValue(stateFile.toFile(), MAP_TYPE);
            for (Object rawItem : list(state.get("configs"))) {
                if (rawItem instanceof Map<?, ?> rawMap) {
                    Map<String, Object> item = map(rawMap);
                    String configId = normalizeId(text(item.get("configId")));
                    if (StringUtils.hasText(configId)) {
                        configs.put(configId, item);
                    }
                }
            }
        } catch (Exception e) {
            throw new AppException("AGENT_ADMIN_0501", "load agent admin state failed", e);
        }
    }

    private void persistState() {
        if (stateFile == null) {
            return;
        }
        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), exportState());
        } catch (Exception e) {
            throw new AppException("AGENT_ADMIN_0502", "persist agent admin state failed", e);
        }
    }

    private Path resolveStateFile(String configured) {
        String value = configured == null ? "" : configured.trim();
        if (!StringUtils.hasText(value)) {
            value = "data/agent-admin-state.json";
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String normalizeId(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
    }

    private String normalizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int number(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return result;
    }

    private static List<?> list(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private Map<String, Integer> categoryCounts(List<Map<String, Object>> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String category = String.valueOf(item.getOrDefault("category", ""));
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return counts;
    }

    private Map<String, Object> runtimeSections(List<Map<String, Object>> enabledConfigs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentClients", runtimeSection(enabledConfigs, "agent_client"));
        result.put("models", runtimeSection(enabledConfigs, "model"));
        result.put("apis", runtimeSection(enabledConfigs, "api"));
        result.put("systemPrompts", runtimeSection(enabledConfigs, "system_prompt"));
        result.put("advisors", runtimeSection(enabledConfigs, "advisor"));
        result.put("ragOrders", runtimeSection(enabledConfigs, "rag_order"));
        result.put("drawConfigs", runtimeSection(enabledConfigs, "draw_config"));
        return result;
    }

    private Map<String, Object> runtimePolicies() {
        Map<String, Object> codeInterpreter = new LinkedHashMap<>();
        codeInterpreter.put("toolName", "code_interpreter");
        codeInterpreter.put("defaultPermissionProfile", AcademicCodeInterpreterPort.PERMISSION_PROFILE_ANALYSIS);
        codeInterpreter.put("allowedPermissionProfiles", AcademicCodeInterpreterPort.allowedPermissionProfiles());
        codeInterpreter.put("analysis", "read input files and write generated artifacts only through output helpers");
        codeInterpreter.put("workspace", "allow workspace scoped reads and writes when explicitly requested");

        Map<String, Object> scriptRunner = new LinkedHashMap<>();
        scriptRunner.put("toolName", "script_runner");
        scriptRunner.put("registeredSkillOnly", true);
        scriptRunner.put("allowedRuntimes", List.of("python", "node", "shell", "powershell", "bat"));
        scriptRunner.put("defaultTimeoutSeconds", 120);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codeInterpreter", codeInterpreter);
        result.put("scriptRunner", scriptRunner);
        return result;
    }

    private List<Map<String, Object>> runtimeSection(List<Map<String, Object>> enabledConfigs, String category) {
        return enabledConfigs.stream()
                .filter(item -> category.equals(String.valueOf(item.getOrDefault("category", ""))))
                .map(this::runtimeConfig)
                .toList();
    }

    private Map<String, Object> runtimeConfig(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        String category = text(config.get("category"));
        String content = text(config.get("content"));
        result.put("configId", text(config.get("configId")));
        result.put("category", category);
        result.put("name", defaultText(config.get("name"), text(config.get("configId"))));
        result.put("description", text(config.get("description")));
        result.put("enabled", bool(config.get("enabled"), true));
        result.put("orderNo", number(config.get("orderNo"), 0));
        result.put("contentLength", content.length());
        result.put("contentPreview", safePreview(category, "content", content));
        result.put("metadata", maskSensitive(map(config.get("metadata"))));
        result.put("updatedAt", text(config.get("updatedAt")));
        return result;
    }

    private Object maskSensitive(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                Object item = entry.getValue();
                result.put(key, isSensitiveKey(key) ? maskedValue(item) : maskSensitive(item));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::maskSensitive).toList();
        }
        if (value instanceof String textValue) {
            return looksLikeSecret(textValue) ? maskedValue(textValue) : textValue;
        }
        return value;
    }

    private String safePreview(String category, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (isSensitiveKey(key) || looksLikeSecret(value)) {
            return maskedValue(value);
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("credential")
                || normalized.contains("authorization");
    }

    private static boolean looksLikeSecret(String value) {
        String text = value == null ? "" : value.trim();
        return text.matches("(?i).*(sk-[a-z0-9_-]{8,}|ak-[a-z0-9_-]{8,}|bearer\\s+[a-z0-9._-]{8,}).*");
    }

    private static String maskedValue(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= 8) {
            return "******";
        }
        return text.substring(0, Math.min(3, text.length())) + "******" + text.substring(text.length() - 2);
    }
}
