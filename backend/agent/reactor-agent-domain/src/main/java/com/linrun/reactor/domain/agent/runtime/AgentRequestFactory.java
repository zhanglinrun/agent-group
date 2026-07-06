package com.linrun.reactor.domain.agent.runtime;

import com.linrun.reactor.domain.agent.reactor.config.ReactorConfig;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;
import com.linrun.reactor.domain.agent.reactor.util.ChateiUtils;
import com.linrun.reactor.domain.agent.runtime.enums.AgentType;
import com.linrun.reactor.types.agent.visitor.VisitorRequestContext;

/**
 * 把前端 GPT 查询请求整理成 Reactor 运行时请求。
 */
public final class AgentRequestFactory {

    private AgentRequestFactory() {
    }

    public static AgentRequest from(GptQueryReq req, ReactorConfig reactorConfig) {
        if (req.getUser() == null || req.getUser().isBlank()) {
            req.setUser("reactor");
        }
        req.setDeepThink(req.getDeepThink() == null ? 0 : req.getDeepThink());
        req.setTraceId(ChateiUtils.getRequestId(req));

        AgentRequest request = new AgentRequest();
        request.setRequestId(req.getTraceId());
        request.setSessionId(req.getSessionId());
        request.setVisitorId(VisitorRequestContext.currentVisitorId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        request.setSessionFiles(req.getSessionFiles());
        request.setAiAgentId(req.getAiAgentId());

        if ("chat".equalsIgnoreCase(req.getOutputStyle())) {
            request.setAgentType(AgentType.WORKFLOW.getValue());
            request.setSopPrompt("");
        } else {
            Integer agentType = (req.getDeepThink() == null || req.getDeepThink() == 0)
                    ? AgentType.REACT.getValue()
                    : AgentType.PLAN_SOLVE.getValue();
            request.setAgentType(agentType);
            request.setSopPrompt(agentType.equals(AgentType.PLAN_SOLVE.getValue())
                    ? reactorConfig.getReactorSopPrompt()
                    : "");
            request.setBasePrompt(agentType.equals(AgentType.REACT.getValue())
                    ? reactorConfig.getReactorBasePrompt()
                    : "");
        }

        request.setIsStream(true);
        request.setOutputStyle(req.getOutputStyle());
        return request;
    }
}
