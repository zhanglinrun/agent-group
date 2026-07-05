package com.linrun.reactor.application.agent.query;

import org.springframework.stereotype.Service;
import com.linrun.reactor.application.agent.stream.AgentSessionStream;
import com.linrun.reactor.domain.agent.runtime.AgentQueryService;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;

import jakarta.annotation.Resource;

/**
 * GPT 查询应用服务。
 * 通过稳定 runtime seam 进入多智能体查询主链路，避免 case 层继续桥接 legacy reactor service。
 */
@Service
public class GptQueryApplicationService implements IGptQueryApplicationService {

    @Resource
    private AgentQueryService agentQueryService;

    @Override
    public void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream) {
        agentQueryService.queryAgentStreamIncr(params, stream);
    }
}
