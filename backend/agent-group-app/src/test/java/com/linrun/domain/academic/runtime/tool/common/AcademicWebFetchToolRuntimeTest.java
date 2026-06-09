package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.types.exception.AppException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicWebFetchToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldFetchLocalHtmlAndExtractReadableText() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/article", exchange -> {
            byte[] bytes = """
                    <html>
                    <head><title>Agent Group Article</title><style>.x{display:none}</style></head>
                    <body><h1>拼团交易</h1><p>支付成功后等待成团，成团后发放额度。/p></body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/article";
            AcademicWebFetchToolRuntime webFetchTool = new AcademicWebFetchToolRuntime();
            AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
            registry.registerStructured(AcademicWebFetchToolRuntime.definition(), webFetchTool::call);

            AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.WEB_FETCH)
                    .arguments(Map.of("url", url, "maxContentChars", 200))
                    .build());

            Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
            assertTrue(result.isSuccess());
            assertEquals("Agent Group Article", result.getResult().get("title"));
            assertTrue(String.valueOf(result.getResult().get("content")).contains("成团后发放额�?));
            assertEquals(200, metadata.get("statusCode"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectNonHttpUrl() {
        AcademicWebFetchToolRuntime webFetchTool = new AcademicWebFetchToolRuntime();

        AppException exception = assertThrows(AppException.class,
                () -> webFetchTool.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.WEB_FETCH)
                        .arguments(Map.of("url", "file:///etc/passwd"))
                        .build()));

        assertEquals("WEB_FETCH_0001", exception.getCode());
    }
}















