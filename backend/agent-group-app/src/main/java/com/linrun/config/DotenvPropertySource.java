package com.linrun.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DotenvPropertySource {

    private static final String PROPERTY_SOURCE_NAME = "agentGroupDotenv";
    private static final Map<String, String> PROPERTY_ALIASES = Map.ofEntries(
            Map.entry("alipay.gatewayurl", "AGENT_GROUP_ALIPAY_GATEWAY_URL"),
            Map.entry("gatewayurl", "AGENT_GROUP_ALIPAY_GATEWAY_URL"),
            Map.entry("alipay.app_id", "AGENT_GROUP_ALIPAY_APP_ID"),
            Map.entry("app_id", "AGENT_GROUP_ALIPAY_APP_ID"),
            Map.entry("alipay.merchant_private_key", "AGENT_GROUP_ALIPAY_PRIVATE_KEY"),
            Map.entry("merchant_private_key", "AGENT_GROUP_ALIPAY_PRIVATE_KEY"),
            Map.entry("alipay.alipay_public_key", "AGENT_GROUP_ALIPAY_PUBLIC_KEY"),
            Map.entry("alipay_public_key", "AGENT_GROUP_ALIPAY_PUBLIC_KEY"),
            Map.entry("alipay.notify_url", "AGENT_GROUP_ALIPAY_NOTIFY_URL"),
            Map.entry("notify_url", "AGENT_GROUP_ALIPAY_NOTIFY_URL"),
            Map.entry("alipay.return_url", "AGENT_GROUP_ALIPAY_RETURN_URL"),
            Map.entry("return_url", "AGENT_GROUP_ALIPAY_RETURN_URL")
    );

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
            environment.getPropertySources()
                    .addBefore(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            environment.getPropertySources().addLast(propertySource);
        }
    }

    private static List<Path> candidateDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        codeSourceDirectory().ifPresent(path -> addProjectScopedAncestors(path, directories));
        addProjectScopedAncestors(Path.of(System.getProperty("user.dir", ".")), directories);
        return List.copyOf(directories);
    }

    private static Optional<Path> codeSourceDirectory() {
        try {
            URL location = DotenvPropertySource.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return Optional.empty();
            }
            Path path = Path.of(location.toURI()).toAbsolutePath().normalize();
            return Optional.of(Files.isRegularFile(path) ? path.getParent() : path);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void addProjectScopedAncestors(Path start, Set<Path> directories) {
        if (start == null) {
            return;
        }
        List<Path> ancestors = new ArrayList<>();
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            ancestors.add(current);
            if (isProjectRoot(current)) {
                break;
            }
            current = current.getParent();
        }
        Collections.reverse(ancestors);
        directories.addAll(ancestors);
    }

    private static boolean isProjectRoot(Path directory) {
        return Files.exists(directory.resolve(".git")) || Files.isRegularFile(directory.resolve("AGENTS.md"));
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
            // 无法读取单个 .env 文件时继续启动。
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
        if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            return;
        }
        String value = cleanEnvValue(text.substring(splitIndex + 1).trim());
        values.put(key, value);
        String alias = PROPERTY_ALIASES.get(key.toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(alias)) {
            values.put(alias, value);
        }
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
