package com.linrun.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class LlmBaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_NAME = "spring.ai.openai.base-url";
    private static final String PROPERTY_SOURCE_NAME = "agentGroupLlmBaseUrlNormalizer";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        DotenvPropertySource.addTo(environment);
        String normalized = normalize(environment.getProperty(PROPERTY_NAME));
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(PROPERTY_NAME, normalized);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    private String normalize(String value) {
        String text = value == null ? "" : value.trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.regionMatches(true, 0, "ttps://", 0, "ttps://".length())) {
            text = "h" + text;
        }
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            text = "https://" + text.replaceFirst("^/+", "");
        }
        return text.replaceAll("/+$", "");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}















