package com.linrun.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 读取运营端在后台保存的模型覆盖配置（data/llm-admin-override.properties），
 * 以最高优先级注入 spring.ai.openai.* 相关属性，使后台填写的 API_KEY / BASE_URL / MODEL
 * 在重启后端后覆盖 .env 与 application.yml 的默认值。
 *
 * 优先级设计：本源 addFirst 到 Environment 最前，因此后台填了就以后台为准；
 * 文件不存在或字段为空时不注入任何属性，回退到 .env / application.yml。
 *
 * 必须在 {@link LlmBaseUrlEnvironmentPostProcessor} 之前执行，
 * 以便 base-url 归一化逻辑能拿到后台覆盖后的值。
 */
public class LlmAdminOverrideEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "agentGroupLlmAdminOverride";
    private static final String OVERRIDE_FILE_PATH = "data/llm-admin-override.properties";

    /**
     * 后台覆盖字段 → Spring AI / 项目属性。
     * 文本模型与嵌入模型共用同一组 api-key / base-url（Spring AI OpenAI 自动配置按此设计）；
     * 图像模型走项目自写 ImageGenerationService，单独一组 key/url/model。
     */
    private static final Map<String, String> PROPERTY_MAPPING = Map.ofEntries(
            Map.entry("chat_api_key", "spring.ai.openai.api-key"),
            Map.entry("chat_base_url", "spring.ai.openai.base-url"),
            Map.entry("chat_model", "spring.ai.openai.chat.options.model"),
            Map.entry("embedding_model", "spring.ai.openai.embedding.options.model"),
            Map.entry("image_api_key", "agent-group.image.api-key"),
            Map.entry("image_base_url", "agent-group.image.base-url"),
            Map.entry("image_model", "agent-group.image.model")
    );

    /**
     * 兼容旧覆盖文件：早期后台只写 api_key / base_url / model 三个键，按文本模型语义回填。
     */
    private static final Map<String, String> LEGACY_MAPPING = Map.of(
            "api_key", "chat_api_key",
            "base_url", "chat_base_url",
            "model", "chat_model"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> overrides = readOverrides();
        if (overrides.isEmpty()) {
            return;
        }
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, overrides);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources()
                    .addBefore(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            environment.getPropertySources().addFirst(propertySource);
        }
    }

    private Map<String, Object> readOverrides() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        Path file = resolveOverrideFile();
        if (file == null || !Files.isRegularFile(file)) {
            return overrides;
        }
        Properties properties = new Properties();
        try {
            properties.load(Files.newBufferedReader(file, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 覆盖文件读取失败时回退到默认链路，不阻断启动。
            return overrides;
        }
        for (Map.Entry<String, String> entry : PROPERTY_MAPPING.entrySet()) {
            String raw = properties.getProperty(entry.getKey());
            if (StringUtils.hasText(raw)) {
                overrides.put(entry.getValue(), raw.trim());
            }
        }
        // 旧覆盖文件兼容：把 api_key/base_url/model 当作文本模型覆盖注入。
        for (Map.Entry<String, String> entry : LEGACY_MAPPING.entrySet()) {
            String legacyRaw = properties.getProperty(entry.getKey());
            if (StringUtils.hasText(legacyRaw) && !overrides.containsKey(PROPERTY_MAPPING.get(entry.getValue()))) {
                overrides.put(PROPERTY_MAPPING.get(entry.getValue()), legacyRaw.trim());
            }
        }
        return overrides;
    }

    private Path resolveOverrideFile() {
        try {
            return Path.of(System.getProperty("user.dir", "."), OVERRIDE_FILE_PATH)
                    .toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        // 早于 LlmBaseUrlEnvironmentPostProcessor（其 order 为 LOWEST_PRECEDENCE），
        // 这里用 LOWEST_PRECEDENCE - 1 保证先执行；同包内独立处理，互不干扰。
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
