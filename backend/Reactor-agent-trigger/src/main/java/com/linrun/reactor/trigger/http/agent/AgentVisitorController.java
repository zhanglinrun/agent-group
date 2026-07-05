package com.linrun.reactor.trigger.http.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.linrun.reactor.api.response.Response;
import com.linrun.reactor.application.agent.visitor.AnonymousVisitorBootstrapApplicationService;
import com.linrun.reactor.application.agent.visitor.AnonymousVisitorNamingApplicationService;
import com.linrun.reactor.application.agent.visitor.model.AnonymousVisitorProfile;
import com.linrun.reactor.trigger.http.agent.vo.VisitorBootstrapRespVO;
import com.linrun.reactor.trigger.http.agent.vo.VisitorNamingReqVO;
import com.linrun.reactor.types.agent.visitor.VisitorRequestContext;
import com.linrun.reactor.types.enums.ResponseCode;

import jakarta.annotation.Resource;

/**
 * 当前浏览器匿名访客接口。
 */
@RestController
@RequestMapping("/api/agent/visitor")
public class AgentVisitorController {

    @Resource
    private AnonymousVisitorBootstrapApplicationService anonymousVisitorBootstrapApplicationService;

    @Resource
    private AnonymousVisitorNamingApplicationService anonymousVisitorNamingApplicationService;

    @GetMapping("/bootstrap")
    public Response<VisitorBootstrapRespVO> bootstrap() {
        AnonymousVisitorProfile profile = anonymousVisitorBootstrapApplicationService.bootstrap(
                VisitorRequestContext.requireVisitorId()
        );
        return Response.<VisitorBootstrapRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(toRespVO(profile))
                .build();
    }

    @PostMapping("/naming")
    public Response<VisitorBootstrapRespVO> naming(@RequestBody VisitorNamingReqVO request) {
        try {
            AnonymousVisitorProfile profile = anonymousVisitorNamingApplicationService.bindUsername(
                    VisitorRequestContext.requireVisitorId(),
                    request == null ? null : request.getUsername()
            );
            return Response.<VisitorBootstrapRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toRespVO(profile))
                    .build();
        } catch (Exception e) {
            return Response.<VisitorBootstrapRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    private VisitorBootstrapRespVO toRespVO(AnonymousVisitorProfile profile) {
        if (profile == null) {
            return null;
        }
        return VisitorBootstrapRespVO.builder()
                .visitorId(profile.getVisitorId())
                .username(profile.getUsername())
                .named(profile.isNamed())
                .build();
    }
}
