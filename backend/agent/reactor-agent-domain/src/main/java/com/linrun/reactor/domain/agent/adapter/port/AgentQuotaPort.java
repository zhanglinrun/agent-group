package com.linrun.reactor.domain.agent.adapter.port;

import com.linrun.reactor.domain.agent.runtime.quota.AgentTokenUsageAccumulator;

/**
 * Agent 额度端口。
 */
public interface AgentQuotaPort {

    void precheck(String userId, String taskType);

    void settle(String userId,
                String sessionId,
                String taskConsumeBizId,
                String taskType,
                AgentTokenUsageAccumulator.UsageSnapshot usageSnapshot);
}
