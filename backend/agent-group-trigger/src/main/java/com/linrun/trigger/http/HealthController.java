package com.linrun.trigger.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-12 下午5:21
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String health() {
        return "agent-group start success";
    }

}