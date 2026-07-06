package com.linrun.reactor.application.agent.query;

import com.linrun.reactor.application.agent.stream.AgentSessionStream;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;

/**
 * GPT 查询应用服务接口。
 * 该 seam 是 trigger 进入查询主链路的唯一入口，不允许再直接依赖已删除的 legacy reactor bridge。
 */
public interface IGptQueryApplicationService {

    void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream);
}
