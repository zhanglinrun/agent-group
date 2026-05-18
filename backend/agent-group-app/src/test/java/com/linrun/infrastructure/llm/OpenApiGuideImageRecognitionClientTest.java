package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiGuideImageRecognitionClientTest {

    @Test
    void shouldReturnBlankWhenApiKeyMissing() {
        OpenApiGuideImageRecognitionClient client = new OpenApiGuideImageRecognitionClient(
                "http://127.0.0.1:18080/",
                "",
                "qwen3-vl-plus",
                Duration.ofSeconds(2),
                HttpClient.newHttpClient(),
                new ObjectMapper());

        assertEquals("", client.recognize("https://example.com/pad.png"));
    }

    @Test
    void shouldCallOpenApiVisionModel() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (MockVisionServer server = MockVisionServer.start(requestBody)) {
            OpenApiGuideImageRecognitionClient client = new OpenApiGuideImageRecognitionClient(
                    server.baseUrl(),
                    "test-api-key",
                    "qwen3-vl-plus",
                    Duration.ofSeconds(2),
                    HttpClient.newHttpClient(),
                    new ObjectMapper());

            String summary = client.recognize("https://example.com/pad.png");

            assertEquals("图片中包含平板商品和拼团价。", summary);
            assertTrue(requestBody.get().contains("\"model\":\"qwen3-vl-plus\""));
            assertTrue(requestBody.get().contains("https://example.com/pad.png"));
        }
    }

    private record MockVisionServer(HttpServer server) implements AutoCloseable {

        static MockVisionServer start(AtomicReference<String> requestBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> handle(exchange, requestBody));
            server.start();
            return new MockVisionServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handle(HttpExchange exchange, AtomicReference<String> requestBody) throws IOException {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String responseBody = """
                    {"choices":[{"message":{"content":"图片中包含平板商品和拼团价。"}}]}
                    """;
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
