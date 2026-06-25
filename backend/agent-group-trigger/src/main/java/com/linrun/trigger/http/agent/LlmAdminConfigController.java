package com.linrun.trigger.http.agent;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营端默认模型配置接口：查询当前生效值、保存覆盖值（重启后端后生效）。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent/admin/llm")
public class LlmAdminConfigController {

    private final LlmAdminConfigService llmAdminConfigService;

    public LlmAdminConfigController(LlmAdminConfigService llmAdminConfigService) {
        this.llmAdminConfigService = llmAdminConfigService;
    }

    @GetMapping("/config")
    public Response<Map<String, Object>> currentConfig() {
        return Response.success(llmAdminConfigService.currentConfig(), RequestTraceContext.getRequestId());
    }

    @PostMapping("/config")
    public Response<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> request) {
        return Response.success(llmAdminConfigService.saveConfig(request), RequestTraceContext.getRequestId());
    }
}
