package com.linrun.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotenvPropertySourceTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @BeforeEach
    void rememberUserDir() {
        originalUserDir = System.getProperty("user.dir");
    }

    @AfterEach
    void restoreUserDir() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void loadsRootDotenvWhenStartedFromNestedDirectory() throws IOException {
        Path projectRoot = tempDir.resolve("agent-group");
        Path nestedWorkingDirectory = projectRoot.resolve("backend/agent-group-app/target/classes");
        Files.createDirectories(nestedWorkingDirectory);
        Files.writeString(projectRoot.resolve("AGENTS.md"), "# test\n");
        Files.writeString(projectRoot.resolve(".env"), "DOTENV_TEST_KEY=from-dotenv\n");
        System.setProperty("user.dir", nestedWorkingDirectory.toString());

        StandardEnvironment environment = environmentWithSystemEnv("DOTENV_TEST_KEY", "from-system");

        DotenvPropertySource.addTo(environment);

        assertEquals("from-dotenv", environment.getProperty("DOTENV_TEST_KEY"));
    }

    @Test
    void localDotenvOverridesBaseDotenv() throws IOException {
        Path projectRoot = tempDir.resolve("agent-group");
        Files.createDirectories(projectRoot);
        Files.writeString(projectRoot.resolve("AGENTS.md"), "# test\n");
        Files.writeString(projectRoot.resolve(".env"), "DOTENV_LOCAL_TEST_KEY=from-env\n");
        Files.writeString(projectRoot.resolve(".env.local"), "DOTENV_LOCAL_TEST_KEY=from-env-local\n");
        System.setProperty("user.dir", projectRoot.toString());

        StandardEnvironment environment = new StandardEnvironment();

        DotenvPropertySource.addTo(environment);

        assertEquals("from-env-local", environment.getProperty("DOTENV_LOCAL_TEST_KEY"));
    }

    private StandardEnvironment environmentWithSystemEnv(String key, String value) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(key, value)));
        return environment;
    }
}
