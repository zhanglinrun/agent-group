package com.linrun.reactor.application.agent.query;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import com.linrun.reactor.application.agent.dispatch.IAgentDispatchService;
import com.linrun.reactor.application.agent.stream.AgentSessionStream;
import com.linrun.reactor.domain.agent.reactor.config.ReactorConfig;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;
import com.linrun.reactor.domain.agent.runtime.AgentRequestFactory;
import com.linrun.reactor.domain.agent.runtime.executor.AgentExecutorSupport;
import com.linrun.reactor.types.agent.config.AgentExecutorNames;
import com.linrun.reactor.types.agent.exception.AgentExecutorBusyException;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.util.concurrent.Executor;

/**
 * GPT 查询应用服务。
 * 负责把前端查询请求转成本地 Reactor 执行请求。
 */
@Slf4j
@Service
public class GptQueryApplicationService implements IGptQueryApplicationService {

    @Resource
    private IAgentDispatchService agentDispatchService;

    @Resource
    private ReactorConfig reactorConfig;

    @Resource(name = AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    @Override
    public void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream) {
        AgentRequest request = AgentRequestFactory.from(params, reactorConfig);
        log.info("{} start local Agent request: {}", params.getRequestId(), JSON.toJSONString(request));
        try {
            AgentExecutorSupport.execute(dispatchExecutor, "dispatch", () -> {
                try {
                    agentDispatchService.dispatch(request, stream);
                    stream.complete();
                } catch (Exception e) {
                    log.error("{} local agent error", request.getRequestId(), e);
                    stream.completeWithError(e);
                }
            });
        } catch (AgentExecutorBusyException e) {
            log.warn("{} dispatch rejected", request.getRequestId(), e);
            stream.completeWithError(e);
        }
    }
}
