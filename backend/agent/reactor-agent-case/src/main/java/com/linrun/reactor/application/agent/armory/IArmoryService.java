package com.linrun.reactor.application.agent.armory;

import com.linrun.reactor.domain.agent.model.valobj.AiAgentVO;

import java.util.List;

/**
 * Agent 应用层装配接口。
 */
public interface IArmoryService {

    List<AiAgentVO> acceptArmoryAllAvailableAgents();

    void acceptArmoryAgent(String agentId);

    List<AiAgentVO> queryAvailableAgents();

    void acceptArmoryAgentClientModelApi(String apiId);
}
