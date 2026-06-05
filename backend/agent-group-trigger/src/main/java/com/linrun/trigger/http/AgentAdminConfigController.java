package com.linrun.trigger.http;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/agent/admin")
public class AgentAdminConfigController {

    private final AgentAdminConfigHandler agentAdminConfigHandler;

    public AgentAdminConfigController(AgentAdminConfigHandler agentAdminConfigHandler) {
        this.agentAdminConfigHandler = agentAdminConfigHandler;
    }

    @GetMapping("/configs")
    public Response<List<Map<String, Object>>> listConfigs(@RequestParam(required = false) String category,
                                                           @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return Response.success(agentAdminConfigHandler.listConfigs(category, enabledOnly), RequestTraceContext.getRequestId());
    }

    @GetMapping("/configs/{configId}")
    public Response<Map<String, Object>> getConfig(@PathVariable String configId) {
        return Response.success(agentAdminConfigHandler.getConfig(configId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/configs")
    public Response<Map<String, Object>> upsertConfig(@RequestBody Map<String, Object> request) {
        return Response.success(agentAdminConfigHandler.upsertConfig(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/configs/{configId}/enabled")
    public Response<Map<String, Object>> enableConfig(@PathVariable String configId,
                                                      @RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request == null ? true : request.getOrDefault("enabled", true)));
        return Response.success(agentAdminConfigHandler.enableConfig(configId, enabled), RequestTraceContext.getRequestId());
    }

    @DeleteMapping("/configs/{configId}")
    public Response<Map<String, Object>> deleteConfig(@PathVariable String configId) {
        return Response.success(agentAdminConfigHandler.deleteConfig(configId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/export")
    public Response<Map<String, Object>> exportState() {
        return Response.success(agentAdminConfigHandler.exportState(), RequestTraceContext.getRequestId());
    }

    @GetMapping("/statistics")
    public Response<Map<String, Object>> statistics() {
        return Response.success(agentAdminConfigHandler.statistics(), RequestTraceContext.getRequestId());
    }

    @GetMapping("/runtime-snapshot")
    public Response<Map<String, Object>> runtimeSnapshot() {
        return Response.success(agentAdminConfigHandler.runtimeSnapshot(), RequestTraceContext.getRequestId());
    }

    @PostMapping("/import")
    public Response<Map<String, Object>> importState(@RequestBody Map<String, Object> request) {
        return Response.success(agentAdminConfigHandler.importState(request), RequestTraceContext.getRequestId());
    }
}
