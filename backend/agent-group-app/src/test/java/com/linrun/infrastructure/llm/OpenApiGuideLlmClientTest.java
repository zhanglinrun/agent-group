package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    void shouldParseUsageAndEstimateCost() throws IOException {
        try (MockLlmServer server = MockLlmServer.start(new AtomicReference<>(), new AtomicReference<>(), 200, """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1000,"completion_tokens":1000,"total_tokens":2000}}
                """)) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    Duration.ofSeconds(2),
                    0,
                    0L,
                    new BigDecimal("0.001"),
                    new BigDecimal("0.002"));

            GuideLlmResult result = client.completeWithMetrics(prompt());

            assertEquals("ok", result.getContent());
            assertEquals(1000L, result.getTokenUsage().getPromptTokens());
            assertEquals(1000L, result.getTokenUsage().getCompletionTokens());
            assertEquals(2000L, result.getTokenUsage().getTotalTokens());
            assertEquals(new BigDecimal("0.003000"), result.getTokenUsage().getEstimatedCostYuan());
            assertTrue(result.getLatencyMillis() >= 0);
        }
    }

    @Test
    void shouldStreamOpenApiCompatibleChatCompletions() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String responseBody = """
                data: {"choices":[{"delta":{"content":"第一段"}}]}

                data: {"choices":[{"delta":{"content":"第二段"}}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}

                data: [DONE]

                """;
        try (MockLlmServer server = MockLlmServer.start(new AtomicReference<>(), requestBody, 200, responseBody)) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper());
            List<String> chunks = new ArrayList<>();

            GuideLlmResult result = client.streamWithMetrics(prompt(), chunks::add, () -> false);

            assertEquals(List.of("第一段", "第二段"), chunks);
            assertEquals("第一段第二段", result.getContent());
            assertEquals(10L, result.getTokenUsage().getPromptTokens());
            assertEquals(20L, result.getTokenUsage().getCompletionTokens());
            assertEquals(30L, result.getTokenUsage().getTotalTokens());
            assertTrue(requestBody.get().contains("\"stream\":true"));
        }
    }

    @Test
    void shouldAcceptBaseUrlThatAlreadyEndsWithV1() throws IOException {
        try (MockLlmServer server = MockLlmServer.start(new AtomicReference<>(), new AtomicReference<>(), 200, """
                {"choices":[{"message":{"content":"兼容 v1 地址"}}]}
                """)) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl() + "v1",
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper());

            assertEquals("兼容 v1 地址", client.complete(prompt()));
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

    @Test
    void shouldRetryWhenOpenApiReturnsServerError() throws IOException {
        AtomicInteger count = new AtomicInteger();
        try (MockLlmServer server = MockLlmServer.startSequence(count)) {
            OpenApiGuideLlmClient client = new OpenApiGuideLlmClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen-plus",
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    Duration.ofSeconds(2),
                    1,
                    0L);

            assertEquals("重试后回答", client.complete(prompt()));
            assertEquals(2, count.get());
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

        static MockLlmServer startSequence(AtomicInteger count) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                int current = count.incrementAndGet();
                int status = current == 1 ? 500 : 200;
                String responseBody = current == 1 ? "{}" : """
                        {"choices":[{"message":{"content":"重试后回答"}}]}
                        """;
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
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
