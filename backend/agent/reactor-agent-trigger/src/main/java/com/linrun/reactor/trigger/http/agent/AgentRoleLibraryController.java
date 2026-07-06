package com.linrun.reactor.trigger.http.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.linrun.reactor.api.response.Response;
import com.linrun.reactor.application.agent.role.IFixRoleQueryService;
import com.linrun.reactor.domain.agent.model.valobj.FixRoleVO;
import com.linrun.reactor.trigger.http.agent.vo.FixRoleRespVO;
import com.linrun.reactor.types.enums.ResponseCode;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fix 角色库接口
 */
@RestController
@RequestMapping("/api/agent/role-library")
public class AgentRoleLibraryController {

    @Resource
    private IFixRoleQueryService fixRoleQueryService;

    @GetMapping("/list")
    public Response<List<FixRoleRespVO>> list() {
        List<FixRoleRespVO> roles = fixRoleQueryService.queryAvailableRoles().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());

        return Response.<List<FixRoleRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(roles)
                .build();
    }

    private FixRoleRespVO toRespVO(FixRoleVO roleVO) {
        return FixRoleRespVO.builder()
                .agentId(roleVO.getAgentId())
                .agentName(roleVO.getAgentName())
                .description(roleVO.getDescription())
                .defaultRole(roleVO.isDefaultRole())
                .build();
    }
}
