package com.linrun.reactor.domain.agent.runtime.handler;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.linrun.reactor.domain.agent.reactor.model.multi.EventResult;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.reactor.model.response.AgentResponse;
import com.linrun.reactor.domain.agent.reactor.model.response.GptProcessResult;
import com.linrun.reactor.domain.agent.ledger.replay.ReplayProjector;

import java.util.List;

@Component
@Slf4j
public class PlanSolveAgentResponseHandler extends BaseAgentResponseHandler implements AgentResponseHandler {

    public PlanSolveAgentResponseHandler(ReplayProjector replayProjector) {
        super(replayProjector);
    }

    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, List<AgentResponse> agentRespList, EventResult eventResult) {
        try {
            return buildCanonicalIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} PlanSolveAgentResponseHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
