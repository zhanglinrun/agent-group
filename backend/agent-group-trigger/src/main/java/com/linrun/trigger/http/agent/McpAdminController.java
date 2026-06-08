package com.linrun.trigger.http.agent;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/mcp/admin")
public class McpAdminController {

    private final McpAdminHandler mcpAdminHandler;

    public McpAdminController(McpAdminHandler mcpAdminHandler) {
        this.mcpAdminHandler = mcpAdminHandler;
    }

    @GetMapping("/servers")
    public Response<List<Map<String, Object>>> listServers() {
        return Response.success(mcpAdminHandler.listServers(), RequestTraceContext.getRequestId());
    }

    @PostMapping("/servers")
    public Response<Map<String, Object>> registerServer(@RequestBody Map<String, Object> request) {
        return Response.success(mcpAdminHandler.registerServer(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/servers/{serverId}/enabled")
    public Response<Map<String, Object>> enableServer(@PathVariable String serverId,
                                                      @RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request == null ? true : request.getOrDefault("enabled", true)));
        return Response.success(mcpAdminHandler.enableServer(serverId, enabled), RequestTraceContext.getRequestId());
    }

    @PostMapping("/servers/{serverId}/tools/cache")
    public Response<Map<String, Object>> cacheTools(@PathVariable String serverId,
                                                    @RequestBody Map<String, Object> request) {
        return Response.success(mcpAdminHandler.cacheTools(serverId, request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/servers/{serverId}/tools/discover")
    public Response<Map<String, Object>> discoverTools(@PathVariable String serverId,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Response.success(mcpAdminHandler.discoverTools(serverId, request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/tools")
    public Response<List<Map<String, Object>>> listTools(@RequestParam(required = false) String serverId,
                                                         @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return Response.success(mcpAdminHandler.listTools(serverId, enabledOnly), RequestTraceContext.getRequestId());
    }

    @GetMapping("/health")
    public Response<Map<String, Object>> health() {
        return Response.success(mcpAdminHandler.health(), RequestTraceContext.getRequestId());
    }

    @GetMapping("/export")
    public Response<Map<String, Object>> exportState() {
        return Response.success(mcpAdminHandler.exportState(), RequestTraceContext.getRequestId());
    }

    @PostMapping("/import")
    public Response<Map<String, Object>> importState(@RequestBody Map<String, Object> request) {
        return Response.success(mcpAdminHandler.importState(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/tools/{toolName}/call")
    public Response<Map<String, Object>> callTool(@PathVariable String toolName,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Response.success(mcpAdminHandler.callRegisteredTool(toolName, request), RequestTraceContext.getRequestId());
    }
}
