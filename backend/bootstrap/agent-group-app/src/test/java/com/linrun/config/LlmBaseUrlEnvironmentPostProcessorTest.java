package com.linrun.config;

import com.linrun.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmBaseUrlEnvironmentPostProcessorTest {

    private final LlmBaseUrlEnvironmentPostProcessor processor = new LlmBaseUrlEnvironmentPostProcessor();

    @Test
    void fixesMissingHInHttpsScheme() {
        StandardEnvironment environment = environmentWithBaseUrl("ttps://dashscope.aliyuncs.com/compatible-mode/v1/");

        processor.postProcessEnvironment(environment, new SpringApplication(Application.class));

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1",
                environment.getProperty("spring.ai.openai.base-url"));
    }

    @Test
    void addsHttpsSchemeWhenMissing() {
        StandardEnvironment environment = environmentWithBaseUrl("dashscope.aliyuncs.com/compatible-mode/v1");

        processor.postProcessEnvironment(environment, new SpringApplication(Application.class));

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1",
                environment.getProperty("spring.ai.openai.base-url"));
    }

    private StandardEnvironment environmentWithBaseUrl(String baseUrl) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("spring.ai.openai.base-url", baseUrl)));
        return environment;
    }
}















