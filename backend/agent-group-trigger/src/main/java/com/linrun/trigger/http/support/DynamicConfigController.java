package com.linrun.trigger.http.support;

import com.linrun.api.dto.DynamicConfigResponse;
import com.linrun.domain.support.config.model.DynamicConfig;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/gbm/dcc")
public class DynamicConfigController {

    private final DynamicConfigService dynamicConfigService;

    public DynamicConfigController(DynamicConfigService dynamicConfigService) {
        this.dynamicConfigService = dynamicConfigService;
    }

    @GetMapping("/update_config")
    public Response<DynamicConfigResponse> updateConfig(@RequestParam String key, @RequestParam String value) {
        return Response.success(toResponse(dynamicConfigService.updateConfig(key, value)), RequestTraceContext.getRequestId());
    }

    @GetMapping("/query_config")
    public Response<DynamicConfigResponse> queryConfig(@RequestParam String key) {
        return Response.success(toResponse(dynamicConfigService.queryConfig(key).orElseThrow()), RequestTraceContext.getRequestId());
    }

    private DynamicConfigResponse toResponse(DynamicConfig config) {
        DynamicConfigResponse response = new DynamicConfigResponse();
        response.setConfigKey(config.getConfigKey());
        response.setConfigValue(config.getConfigValue());
        response.setRemark(config.getRemark());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }
}
