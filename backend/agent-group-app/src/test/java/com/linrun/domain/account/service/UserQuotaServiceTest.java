package com.linrun.domain.account.service;

import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class UserQuotaServiceTest {

    @Test
    void consumeForSameSessionUsesDifferentFlowBizId() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.TEN);
        UserQuotaService service = new UserQuotaService(
                quotaRepository,
                mock(GuideDataRepository.class),
                mock(TradeOrderRepository.class));
        GuideTokenUsage usage = new GuideTokenUsage(10L, 20L, 30L, BigDecimal.ZERO);

        service.consumeForAcademicTask("U10001", "AS1780458958046", "chat", usage, "test-model", 100L);
        service.consumeForAcademicTask("U10001", "AS1780458958046", "chat", usage, "test-model", 120L);

        assertEquals(2, quotaRepository.flows.size());
        assertNotEquals(quotaRepository.flows.get(0).getBizId(), quotaRepository.flows.get(1).getBizId());
        assertEquals("AS1780458958046", quotaRepository.usages.get(0).getSessionId());
        assertEquals("AS1780458958046", quotaRepository.usages.get(1).getSessionId());
    }

    @Test
    void tradeAuditPreCheckUsesTradeTaskCost() {
        UserQuotaService service = new UserQuotaService(
                new InMemoryQuotaRepository(BigDecimal.TEN),
                mock(GuideDataRepository.class),
                mock(TradeOrderRepository.class));

        assertEquals(BigDecimal.valueOf(2), service.estimatePreCheckCost("trade-audit"));
    }

    private static class InMemoryQuotaRepository implements UserQuotaRepository {
        private BigDecimal balance;
        private BigDecimal used = BigDecimal.ZERO;
        private final List<UserQuotaFlow> flows = new ArrayList<>();
        private final List<ModelUsageRecord> usages = new ArrayList<>();
        private final Set<String> flowKeys = new HashSet<>();

        private InMemoryQuotaRepository(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public void createAccountIfAbsent(String userId) {
        }

        @Override
        public Optional<UserQuotaAccount> queryAccount(String userId) {
            UserQuotaAccount account = new UserQuotaAccount();
            account.setUserId(userId);
            account.setQuotaBalance(balance);
            account.setUsedQuota(used);
            return Optional.of(account);
        }

        @Override
        public int increaseQuota(String userId, BigDecimal amount) {
            balance = balance.add(amount);
            return 1;
        }

        @Override
        public int decreaseQuota(String userId, BigDecimal amount) {
            if (balance.compareTo(amount) < 0) {
                return 0;
            }
            balance = balance.subtract(amount);
            used = used.add(amount);
            return 1;
        }

        @Override
        public int decreaseQuotaAllowNegative(String userId, BigDecimal amount) {
            balance = balance.subtract(amount);
            used = used.add(amount);
            return 1;
        }

        @Override
        public void saveFlow(UserQuotaFlow flow) {
            String key = flow.getUserId() + "-" + flow.getFlowType() + "-" + flow.getBizId();
            if (!flowKeys.add(key)) {
                throw new IllegalStateException("Duplicate entry '" + key + "' for key 'user_quota_flow.uk_user_biz_flow'");
            }
            flows.add(flow);
        }

        @Override
        public Optional<UserQuotaFlow> queryFlow(String userId, String flowType, String bizId) {
            return flows.stream()
                    .filter(flow -> flow.getUserId().equals(userId)
                            && flow.getFlowType().equals(flowType)
                            && flow.getBizId().equals(bizId))
                    .findFirst();
        }

        @Override
        public List<UserQuotaFlow> queryRecentFlows(String userId, int limit) {
            return flows.stream().limit(limit).toList();
        }

        @Override
        public void saveUsage(ModelUsageRecord usageRecord) {
            usages.add(usageRecord);
        }
    }
}
