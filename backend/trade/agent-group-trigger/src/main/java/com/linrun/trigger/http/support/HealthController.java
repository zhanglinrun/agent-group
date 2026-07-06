package com.linrun.trigger.http.support;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Response<String> health() {
        return Response.success("agent-group start success", RequestTraceContext.getRequestId());
    }
}















