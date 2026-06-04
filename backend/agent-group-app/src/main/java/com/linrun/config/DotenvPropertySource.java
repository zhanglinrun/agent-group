package com.linrun.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DotenvPropertySource {

    private static final String PROPERTY_SOURCE_NAME = "agentGroupDotenv";

    private DotenvPropertySource() {
    }

    static void addTo(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Path directory : candidateDirectories()) {
            readEnvFile(directory.resolve(".env"), values);
            readEnvFile(directory.resolve(".env.local"), values);
        }
        if (values.isEmpty()) {
            return;
        }
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            environment.getPropertySources().addLast(propertySource);
        }
    }

    private static List<Path> candidateDirectories() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path parent = current.getParent();
        Path grandParent = parent == null ? null : parent.getParent();
        return List.of(current, parent, grandParent).stream()
                .filter(path -> path != null)
                .distinct()
                .toList();
    }

    private static void readEnvFile(Path file, Map<String, Object> values) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseEnvLine(line, values);
            }
        } catch (IOException ignored) {
            // 本地 .env 只作为开发期兜底，读取失败时继续走系统环境变量。
        }
    }

    private static void parseEnvLine(String line, Map<String, Object> values) {
        String text = line == null ? "" : line.trim();
        if (!StringUtils.hasText(text) || text.startsWith("#")) {
            return;
        }
        if (text.startsWith("export ")) {
            text = text.substring("export ".length()).trim();
        }
        int splitIndex = text.indexOf('=');
        if (splitIndex <= 0) {
            return;
        }
        String key = text.substring(0, splitIndex).trim();
        if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return;
        }
        values.put(key, cleanEnvValue(text.substring(splitIndex + 1).trim()));
    }

    private static String cleanEnvValue(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        int commentIndex = value.indexOf(" #");
        return commentIndex >= 0 ? value.substring(0, commentIndex).trim() : value;
    }
}
