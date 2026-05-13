package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.guide.model.GuideRagPrompt;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiGuideLlmClientTest {

    @Test
    void shouldFallbackWhenApiKeyMissing() {
        OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                "http://127.0.0.1:18080/",
                "",
                "qwen-plus",
                HttpClient.newHttpClient(),
                new ObjectMapper());

        assertEquals("兜底回答", client.complete(prompt()));
    }

    @Test
    void shouldCallOpenApiCompatibleChatCompletions() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (MockLlmServer server = MockLlmServer.start(authorization, requestBody, 200, """
                {"choices":[{"message":{"content":"真实模型回答"}}]}
                """)) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper());

            String answer = client.complete(prompt());

            assertEquals("真实模型回答", answer);
            assertEquals("Bearer test-api-key", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"qwen-plus\""));
            assertTrue(requestBody.get().contains("用户提示词"));
        }
    }

    @Test
    void shouldFallbackWhenOpenApiReturnsError() throws IOException {
        try (MockLlmServer server = MockLlmServer.start(new AtomicReference<>(), new AtomicReference<>(), 500, "{}")) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper());

            assertEquals("兜底回答", client.complete(prompt()));
        }
    }

    private GuideRagPrompt prompt() {
        GuideRagPrompt prompt = new GuideRagPrompt();
        prompt.setSystemPrompt("系统提示词");
        prompt.setUserPrompt("用户提示词");
        prompt.setFallbackAnswer("兜底回答");
        return prompt;
    }

    private record MockLlmServer(HttpServer server) implements AutoCloseable {

        static MockLlmServer start(AtomicReference<String> authorization,
                                   AtomicReference<String> requestBody,
                                   int status,
                                   String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> handle(exchange, authorization, requestBody,
                    status, responseBody));
            server.start();
            return new MockLlmServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handle(HttpExchange exchange,
                                   AtomicReference<String> authorization,
                                   AtomicReference<String> requestBody,
                                   int status,
                                   String responseBody) throws IOException {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
