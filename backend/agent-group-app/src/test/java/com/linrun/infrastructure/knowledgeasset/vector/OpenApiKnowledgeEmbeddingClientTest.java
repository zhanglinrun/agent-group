package com.linrun.infrastructure.knowledgeasset.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiKnowledgeEmbeddingClientTest {

    @Test
    void shouldFallbackWhenApiKeyMissing() {
        OpenApiKnowledgeEmbeddingClient client = new OpenApiKnowledgeEmbeddingClient(
                "http://127.0.0.1:18080/",
                "",
                "text-embedding-v4",
                Duration.ofSeconds(2),
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                new LocalKnowledgeEmbeddingClient(16));

        assertEquals(16, client.embed("退款").size());
    }

    @Test
    void shouldCallOpenApiCompatibleEmbeddingEndpoint() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (MockEmbeddingServer server = MockEmbeddingServer.start(authorization, requestBody, 200, """
                {"data":[{"embedding":[0.1,0.2,0.3]}]}
                """)) {
            OpenApiKnowledgeEmbeddingClient client = new OpenApiKnowledgeEmbeddingClient(
                    server.baseUrl(),
                    "test-api-key",
                    "text-embedding-v4",
                    Duration.ofSeconds(2),
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    new LocalKnowledgeEmbeddingClient(16));

            List<Double> embedding = client.embed("拼团退款");

            assertEquals(List.of(0.1d, 0.2d, 0.3d), embedding);
            assertEquals("Bearer test-api-key", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"text-embedding-v4\""));
            assertTrue(requestBody.get().contains("\"dimensions\":1024"));
            assertTrue(requestBody.get().contains("\"encoding_format\":\"float\""));
            assertTrue(requestBody.get().contains("拼团退款"));
        }
    }

    @Test
    void shouldAcceptBaseUrlThatAlreadyEndsWithV1() throws IOException {
        try (MockEmbeddingServer server = MockEmbeddingServer.start(new AtomicReference<>(), new AtomicReference<>(), 200, """
                {"data":[{"embedding":[0.4,0.5]}]}
                """)) {
            OpenApiKnowledgeEmbeddingClient client = new OpenApiKnowledgeEmbeddingClient(
                    server.baseUrl() + "v1/",
                    "test-api-key",
                    "text-embedding-v4",
                    Duration.ofSeconds(2),
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    new LocalKnowledgeEmbeddingClient(16));

            assertEquals(List.of(0.4d, 0.5d), client.embed("拼团退款"));
        }
    }

    @Test
    void shouldFallbackWhenEmbeddingEndpointFails() throws IOException {
        try (MockEmbeddingServer server = MockEmbeddingServer.start(new AtomicReference<>(), new AtomicReference<>(), 500, "{}")) {
            OpenApiKnowledgeEmbeddingClient client = new OpenApiKnowledgeEmbeddingClient(
                    server.baseUrl(),
                    "test-api-key",
                    "text-embedding-v4",
                    Duration.ofSeconds(2),
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    new LocalKnowledgeEmbeddingClient(16));

            assertEquals(16, client.embed("拼团退款").size());
        }
    }

    private record MockEmbeddingServer(HttpServer server) implements AutoCloseable {

        static MockEmbeddingServer start(AtomicReference<String> authorization,
                                         AtomicReference<String> requestBody,
                                         int status,
                                         String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/embeddings", exchange -> handle(exchange, authorization, requestBody,
                    status, responseBody));
            server.start();
            return new MockEmbeddingServer(server);
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
