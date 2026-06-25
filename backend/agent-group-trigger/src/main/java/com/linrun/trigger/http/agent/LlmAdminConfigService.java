package com.linrun.trigger.http.agent;

import com.linrun.types.exception.AppException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 运营端默认模型配置的读写服务。
 *
 * 后台保存的 API_KEY / BASE_URL / MODEL 写入 data/llm-admin-override.properties，
 * 由 {@code LlmAdminOverrideEnvironmentPostProcessor} 在下次启动时注入 Environment，
 * 覆盖 .env 与 application.yml。保存后不重建 Bean，需重启后端生效。
 *
 * 当前生效值直接从 Environment 读取（已包含覆盖源、.env、yml 的解析结果）。
 */
@Service
public class LlmAdminConfigService {

    private static final String OVERRIDE_FILE_PATH = "data/llm-admin-override.properties";

    // Spring AI / 项目属性名：文本与嵌入共用 api-key / base-url，图像单独一组。
    private static final String PROP_CHAT_API_KEY = "spring.ai.openai.api-key";
    private static final String PROP_CHAT_BASE_URL = "spring.ai.openai.base-url";
    private static final String PROP_CHAT_MODEL = "spring.ai.openai.chat.options.model";
    private static final String PROP_EMBEDDING_MODEL = "spring.ai.openai.embedding.options.model";
    private static final String PROP_IMAGE_API_KEY = "agent-group.image.api-key";
    private static final String PROP_IMAGE_BASE_URL = "agent-group.image.base-url";
    private static final String PROP_IMAGE_MODEL = "agent-group.image.model";

    private final Environment environment;
    private final Path overrideFile;

    public LlmAdminConfigService(Environment environment) {
        this.environment = environment;
        this.overrideFile = Path.of(System.getProperty("user.dir", "."), OVERRIDE_FILE_PATH)
                .toAbsolutePath().normalize();
    }

    /**
     * 返回当前生效的模型配置（从 Environment 读取，API_KEY 做脱敏）。
     */
    public Map<String, Object> currentConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chat", modelView(
                mask(environment.getProperty(PROP_CHAT_API_KEY)),
                trim(environment.getProperty(PROP_CHAT_BASE_URL)),
                trim(environment.getProperty(PROP_CHAT_MODEL))));
        result.put("embedding", modelView(
                mask(environment.getProperty(PROP_CHAT_API_KEY)),
                trim(environment.getProperty(PROP_CHAT_BASE_URL)),
                trim(environment.getProperty(PROP_EMBEDDING_MODEL))));
        result.put("image", modelView(
                mask(environment.getProperty(PROP_IMAGE_API_KEY)),
                trim(environment.getProperty(PROP_IMAGE_BASE_URL)),
                trim(environment.getProperty(PROP_IMAGE_MODEL))));
        result.put("overrideFile", overrideFile.toString());
        result.put("persisted", readPersistedOverrides());
        result.put("requiresRestart", false);
        return result;
    }

    /**
     * 保存后台填写的覆盖值。空字符串视为「清除该字段覆盖」，回退到 .env / yml。
     * 入参结构：{ chat:{apiKey,baseUrl,model}, embedding:{model}, image:{apiKey,baseUrl,model} }。
     */
    public Map<String, Object> saveConfig(Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        Properties properties = readPersistedProperties();
        Map<String, Object> chat = asMap(body.get("chat"));
        Map<String, Object> embedding = asMap(body.get("embedding"));
        Map<String, Object> image = asMap(body.get("image"));
        applyOverride(properties, "chat_api_key", chat.get("apiKey"));
        applyOverride(properties, "chat_base_url", chat.get("baseUrl"));
        applyOverride(properties, "chat_model", chat.get("model"));
        applyOverride(properties, "embedding_model", embedding.get("model"));
        applyOverride(properties, "image_api_key", image.get("apiKey"));
        applyOverride(properties, "image_base_url", image.get("baseUrl"));
        applyOverride(properties, "image_model", image.get("model"));
        persist(properties);
        return currentConfig();
    }

    private void applyOverride(Properties properties, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.hasText(text)) {
            properties.setProperty(key, text);
        } else {
            properties.remove(key);
        }
    }

    private Properties readPersistedProperties() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(overrideFile)) {
            return properties;
        }
        try {
            properties.load(Files.newBufferedReader(overrideFile, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AppException("LLM_ADMIN_0501", "读取模型覆盖配置失败", e);
        }
        return properties;
    }

    private Map<String, Object> readPersistedOverrides() {
        Properties properties = readPersistedProperties();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chat", modelView(
                mask(trim(properties.getProperty("chat_api_key"))),
                trim(properties.getProperty("chat_base_url")),
                trim(properties.getProperty("chat_model"))));
        result.put("embedding", modelView(
                "",
                "",
                trim(properties.getProperty("embedding_model"))));
        result.put("image", modelView(
                mask(trim(properties.getProperty("image_api_key"))),
                trim(properties.getProperty("image_base_url")),
                trim(properties.getProperty("image_model"))));
        return result;
    }

    private static Map<String, Object> modelView(String apiKey, String baseUrl, String model) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("apiKey", apiKey);
        view.put("baseUrl", baseUrl);
        view.put("model", model);
        return view;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typed;
        }
        return Map.of();
    }

    private void persist(Properties properties) {
        try {
            Path parent = overrideFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            properties.store(Files.newBufferedWriter(overrideFile, StandardCharsets.UTF_8),
                    "agent-group llm admin override (managed by AdminDashboard)");
        } catch (Exception e) {
            throw new AppException("LLM_ADMIN_0502", "保存模型覆盖配置失败", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 8) {
            return "******";
        }
        return value.substring(0, Math.min(3, value.length())) + "******" + value.substring(value.length() - 2);
    }
}
