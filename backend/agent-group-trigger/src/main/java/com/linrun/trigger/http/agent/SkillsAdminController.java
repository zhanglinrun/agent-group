package com.linrun.trigger.http.agent;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营端技能管理接口：列出已注册技能、启用/禁用技能。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent/admin/skills")
public class SkillsAdminController {

    private final SkillsAdminHandler skillsAdminHandler;

    public SkillsAdminController(SkillsAdminHandler skillsAdminHandler) {
        this.skillsAdminHandler = skillsAdminHandler;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> listSkills() {
        return Response.success(skillsAdminHandler.listSkills(), RequestTraceContext.getRequestId());
    }

    @PostMapping("/{skillName}/enabled")
    public Response<Map<String, Object>> setEnabled(@PathVariable String skillName,
                                                    @RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request == null ? true : request.getOrDefault("enabled", true)));
        return Response.success(skillsAdminHandler.setEnabled(skillName, enabled), RequestTraceContext.getRequestId());
    }
}
