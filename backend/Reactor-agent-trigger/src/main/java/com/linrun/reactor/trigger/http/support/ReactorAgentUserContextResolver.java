package com.linrun.reactor.trigger.http.support;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.types.agent.visitor.VisitorRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class ReactorAgentUserContextResolver {

    private final UserAccountService userAccountService;

    public ReactorAgentUserContextResolver(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    public UserAccount requireUser(HttpServletRequest request) {
        return userAccountService.requireUserByToken(resolveAuthorization(request));
    }

    public Optional<UserAccount> resolveUserIfPresent(HttpServletRequest request) {
        String authorization = resolveAuthorization(request);
        if (!StringUtils.hasText(authorization)) {
            return Optional.empty();
        }
        return Optional.of(userAccountService.requireUserByToken(authorization));
    }

    public String resolveAgentUserId(HttpServletRequest request, AgentRequest agentRequest) {
        Optional<UserAccount> authenticatedUser = resolveUserIfPresent(request);
        if (authenticatedUser.isPresent()) {
            return authenticatedUser.get().getUserId();
        }
        if (isLoopbackRequest(request)) {
            return resolveFallbackVisitorId(agentRequest);
        }
        return requireUser(request).getUserId();
    }

    private String resolveFallbackVisitorId(AgentRequest agentRequest) {
        String contextVisitorId = VisitorRequestContext.currentVisitorId();
        String requestVisitorId = agentRequest == null ? null : agentRequest.getVisitorId();
        String visitorId = StringUtils.hasText(contextVisitorId) ? contextVisitorId : requestVisitorId;
        if (!StringUtils.hasText(visitorId)) {
            throw new IllegalArgumentException("visitorId不能为空");
        }
        return visitorId;
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        if (request == null || !StringUtils.hasText(request.getRemoteAddr())) {
            return false;
        }
        String remoteAddress = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress);
    }

    private String resolveAuthorization(HttpServletRequest request) {
        return request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
