package com.linrun.trigger.http;

import com.linrun.trigger.http.McpToolHandler;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/mcp")
public class McpToolController {

    private final McpToolHandler mcpToolService;

    public McpToolController(McpToolHandler mcpToolService) {
        this.mcpToolService = mcpToolService;
    }

    @PostMapping
    public Map<String, Object> handle(@RequestBody Map<String, Object> request) {
        String method = text(request.get("method"));
        Object id = request.get("id");
        return switch (method) {
            case "initialize" -> response(id, Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of("name", "agent-group-mcp", "version", "1.0.0"),
                    "capabilities", Map.of("tools", Map.of())));
            case "ping" -> response(id, Map.of());
            case "tools/list" -> response(id, Map.of("tools", mcpToolService.listTools()));
            case "tools/call" -> response(id, callTool(request));
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(Map<String, Object> request) {
        Map<String, Object> params = asMap(request.get("params"));
        String name = text(params.get("name"));
        Map<String, Object> arguments = asMap(params.get("arguments"));
        return mcpToolService.callTool(name, arguments);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> response(Object id, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
