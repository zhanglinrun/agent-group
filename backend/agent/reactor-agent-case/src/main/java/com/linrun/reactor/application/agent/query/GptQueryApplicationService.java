package com.linrun.reactor.application.agent.query;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.linrun.reactor.application.agent.stream.AgentSessionStream;
import com.linrun.reactor.domain.agent.runtime.AgentQueryService;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;

/**
 * GPT 查询应用服务。
 * 通过稳定 runtime seam 进入多智能体查询主链路，与 ai-agent 保持一致。
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
