package com.linrun.infrastructure.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiEndpointSupportTest {

    @Test
    void shouldBuildUriWhenBaseUrlDoesNotContainV1() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                OpenApiEndpointSupport.uri("https://dashscope.aliyuncs.com/compatible-mode/", "chat/completions").toString());
    }

    @Test
    void shouldBuildUriWhenBaseUrlAlreadyContainsV1() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
                OpenApiEndpointSupport.uri("https://dashscope.aliyuncs.com/compatible-mode/v1", "/embeddings").toString());
    }
}
