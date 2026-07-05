package com.linrun.reactor.domain.agent.runtime.handler;


import com.linrun.reactor.domain.agent.reactor.model.multi.EventResult;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.reactor.model.response.AgentResponse;
import com.linrun.reactor.domain.agent.reactor.model.response.GptProcessResult;

import java.util.List;

public interface AgentResponseHandler {
    GptProcessResult handle(AgentRequest request,
                            AgentResponse response,
                            List<AgentResponse> agentRespList,
                            EventResult eventResult);
}
