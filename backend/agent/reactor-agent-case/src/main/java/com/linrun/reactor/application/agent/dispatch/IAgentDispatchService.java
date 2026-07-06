package com.linrun.reactor.application.agent.dispatch;

import com.linrun.reactor.application.agent.stream.AgentSessionStream;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;

/**
 * Agent 应用层调度接口。
 */
public interface IAgentDispatchService {

    void dispatch(AgentRequest request, AgentSessionStream stream) throws Exception;
}
