package com.linrun.reactor.trigger.quota;

import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.reactor.domain.agent.adapter.port.AgentQuotaPort;
import com.linrun.reactor.domain.agent.runtime.quota.AgentTokenUsageAccumulator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Reactor Agent 与本项目额度账户之间的桥接实现。
 */
@Component
public class AgentGroupQuotaPort implements AgentQuotaPort {

    private final UserQuotaService userQuotaService;

    public AgentGroupQuotaPort(UserQuotaService userQuotaService) {
        this.userQuotaService = userQuotaService;
    }

    @Override
    public void precheck(String userId, String taskType) {
        BigDecimal precheckCost = userQuotaService.estimatePreCheckCost(taskType);
        userQuotaService.assertEnoughQuota(userId, precheckCost);
    }

    @Override
    public void settle(String userId,
                       String sessionId,
                       String taskConsumeBizId,
                       String taskType,
                       AgentTokenUsageAccumulator.UsageSnapshot usageSnapshot) {
        if (usageSnapshot == null || !usageSnapshot.hasTokenUsage()) {
            return;
        }
        TokenUsageMetrics tokenUsage = new TokenUsageMetrics(
                usageSnapshot.promptTokens(),
                usageSnapshot.completionTokens(),
                usageSnapshot.totalTokens(),
                BigDecimal.ZERO
        );
        userQuotaService.consumeForAgentTask(
                userId,
                sessionId,
                taskConsumeBizId,
                taskType,
                tokenUsage,
                usageSnapshot.modelName(),
                usageSnapshot.durationMillis()
        );
    }
}
