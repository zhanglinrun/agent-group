package com.linrun.domain.agent.runtime.agent;

import java.util.List;

@FunctionalInterface
public interface AgentReplanStrategy {

    List<AgentPlanStep> replan(AgentFlowReplanRequest request);
}















