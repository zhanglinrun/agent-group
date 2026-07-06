package com.linrun.reactor.test.domain;

import com.linrun.reactor.domain.agent.reactor.config.ReactorConfig;
import com.linrun.reactor.domain.agent.runtime.ReactorRuntimeDependencies;
import com.linrun.reactor.domain.agent.runtime.llm.LLMSettings;
import com.linrun.reactor.test.domain.support.ReactorRuntimeTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

public class ReactorRuntimeDependenciesTest {

    @Test
    public void testResolveLlmSettingsUsesEnvironmentGatewayOverStoredModelGateway() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "llmSettingsMap", Map.of(
                "local-stub",
                LLMSettings.builder()
                        .model("local-stub")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                        .interfaceUrl("/v1/chat/completions")
                        .apiKey("stored-key")
                        .maxTokens(2048)
                        .functionCallType("function_call")
                        .maxInputTokens(4096)
                        .build()
        ));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.base-url", "http://127.0.0.1:18082")
                .withProperty("spring.ai.openai.api-key", "local-test-key")
                .withProperty("spring.ai.openai.chat.completions-path", "/v1/chat/completions");
        ReactorRuntimeDependencies dependencies =
                ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig, null, environment);

        LLMSettings settings = dependencies.resolveLlmSettings("local-stub");

        Assert.assertEquals("local-stub", settings.getModel());
        Assert.assertEquals("http://127.0.0.1:18082", settings.getBaseUrl());
        Assert.assertEquals("local-test-key", settings.getApiKey());
        Assert.assertEquals("/v1/chat/completions", settings.getInterfaceUrl());
        Assert.assertEquals(2048, settings.getMaxTokens());
    }
}
