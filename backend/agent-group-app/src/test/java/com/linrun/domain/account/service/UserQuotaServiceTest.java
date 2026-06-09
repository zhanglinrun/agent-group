package com.linrun.domain.account.service;

import com.linrun.api.dto.UserModelConfigRequest;
import com.linrun.api.dto.UserModelConfigResponse;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.agent.conversation.adapter.QuotaProductRepository;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.conversation.model.TokenUsageMetrics;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQuotaServiceTest {

    @Test
    void consumeForSameSessionUsesDifferentFlowBizId() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.TEN);
        UserQuotaService service = new UserQuotaService(
                quotaRepository,
                mock(QuotaProductRepository.class),
                mock(TradeOrderRepository.class));
        TokenUsageMetrics usage = new TokenUsageMetrics(10L, 20L, 30L, BigDecimal.ZERO);

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
                mock(QuotaProductRepository.class),
                mock(TradeOrderRepository.class));

        assertEquals(new BigDecimal("0.20"), service.estimatePreCheckCost("trade-audit"));
    }

    @Test
    void modelConfigSeparatesTextAndImageModels() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.TEN);
        UserQuotaService service = new UserQuotaService(
                quotaRepository,
                mock(QuotaProductRepository.class),
                mock(TradeOrderRepository.class));
        ReflectionTestUtils.setField(service, "modelConfigCryptoSecret", "test-model-config-secret");

        UserModelConfigRequest request = new UserModelConfigRequest();
        request.setEnabled(true);
        request.setTextBaseUrl("https://text.example.com/v1");
        request.setTextApiKey("sk-text-secret-1234");
        request.setTextModel("custom-text-model");
        request.setImageBaseUrl("https://image.example.com/v1");
        request.setImageApiKey("sk-image-secret-5678");
        request.setImageModel("custom-image-model");

        UserModelConfigResponse response = service.saveModelConfig("U10001", request);
        Optional<UserModelConfig> runtimeConfig = service.queryRuntimeModelConfig("U10001");

        assertEquals("https://text.example.com/v1", response.getBaseUrl());
        assertEquals("https://text.example.com/v1", response.getTextBaseUrl());
        assertEquals("custom-text-model", response.getModel());
        assertEquals("custom-text-model", response.getTextModel());
        assertEquals("https://image.example.com/v1", response.getImageBaseUrl());
        assertEquals("custom-image-model", response.getImageModel());
        assertEquals("sk-t****1234", response.getTextKeyMasked());
        assertEquals("sk-i****5678", response.getImageKeyMasked());
        assertTrue(runtimeConfig.isPresent());
        assertEquals("https://text.example.com/v1", runtimeConfig.get().getBaseUrl());
        assertEquals("https://text.example.com/v1", runtimeConfig.get().getTextBaseUrl());
        assertEquals("https://image.example.com/v1", runtimeConfig.get().getImageBaseUrl());
        assertEquals("custom-text-model", runtimeConfig.get().getModel());
        assertEquals("custom-text-model", runtimeConfig.get().getTextModel());
        assertEquals("custom-image-model", runtimeConfig.get().getImageModel());
        assertEquals("sk-text-secret-1234", runtimeConfig.get().getApiKey());
        assertEquals("sk-text-secret-1234", runtimeConfig.get().getTextApiKey());
        assertEquals("sk-image-secret-5678", runtimeConfig.get().getImageApiKey());
        assertNotEquals(runtimeConfig.get().getTextApiKey(), runtimeConfig.get().getImageApiKey());
    }

    @Test
    void consumeForSameExplicitBizIdIsIdempotent() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.TEN);
        UserQuotaService service = new UserQuotaService(
                quotaRepository,
                mock(QuotaProductRepository.class),
                mock(TradeOrderRepository.class));
        TokenUsageMetrics usage = new TokenUsageMetrics(500L, 500L, 1000L, BigDecimal.ZERO);

        service.consumeForAcademicTask("U10001", "AS10001", "REQ10001", "chat", usage, "test-model", 100L);
        service.consumeForAcademicTask("U10001", "AS10001", "REQ10001", "chat", usage, "test-model", 120L);

        assertEquals(1, quotaRepository.flows.size());
        assertEquals(new BigDecimal("-0.20"), quotaRepository.flows.get(0).getQuotaAmount());
        assertEquals(new BigDecimal("9.80"), quotaRepository.balance);
        assertEquals(1, quotaRepository.usages.size());
    }

    @Test
    void consumeCoveredByMembershipStillWritesIdempotentFlow() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.ZERO);
        quotaRepository.upsertMembership(activeMembership("U10001", new BigDecimal("10.00")));
        UserQuotaService service = new UserQuotaService(
                quotaRepository,
                mock(QuotaProductRepository.class),
                mock(TradeOrderRepository.class));
        TokenUsageMetrics usage = new TokenUsageMetrics(500L, 500L, 1000L, BigDecimal.ZERO);

        service.consumeForAcademicTask("U10001", "AS10001", "REQ10001", "chat", usage, "test-model", 100L);
        service.consumeForAcademicTask("U10001", "AS10001", "REQ10001", "chat", usage, "test-model", 120L);

        assertEquals(1, quotaRepository.flows.size());
        assertEquals(0, quotaRepository.flows.get(0).getQuotaAmount().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.20"), quotaRepository.membership.getMonthlyUsedQuota());
        assertEquals(1, quotaRepository.usages.size());
    }

    @Test
    void groupOrderGrantsQuotaOnlyAfterSettlementAndIsIdempotent() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.ZERO);
        UserQuotaService service = serviceWithProduct(quotaRepository, quotaProduct("G1001", new BigDecimal("20.00")));
        TradeOrderEntity order = order("O10001", TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.PAY_SUCCESS);

        service.grantQuotaForPaidOrder(order);
        assertEquals(0, quotaRepository.flows.size());
        assertEquals(new BigDecimal("0"), quotaRepository.balance);

        order.setOrderStatus(TradeOrderStatusEnumVO.GROUP_SETTLED);
        service.grantQuotaForPaidOrder(order);
        service.grantQuotaForPaidOrder(order);

        assertEquals(1, quotaRepository.flows.size());
        assertEquals(UserQuotaService.FLOW_ORDER_GRANT, quotaRepository.flows.get(0).getFlowType());
        assertEquals(new BigDecimal("20.00"), quotaRepository.balance);
        assertEquals(TradeOrderStatusEnumVO.DEAL_DONE, order.getOrderStatus());
    }

    @Test
    void refundRollbackUsesGrantFlowAndIsIdempotent() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.ZERO);
        UserQuotaService service = serviceWithProduct(quotaRepository, quotaProduct("G1001", new BigDecimal("20.00")));
        TradeOrderEntity order = order("O10001", TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.PAY_SUCCESS);

        service.grantQuotaForPaidOrder(order);
        service.rollbackQuotaForRefundedOrder(order);
        service.rollbackQuotaForRefundedOrder(order);

        assertEquals(2, quotaRepository.flows.size());
        assertEquals(UserQuotaService.FLOW_REFUND_ROLLBACK, quotaRepository.flows.get(1).getFlowType());
        assertEquals(new BigDecimal("-20.00"), quotaRepository.flows.get(1).getQuotaAmount());
        assertEquals(new BigDecimal("0.00"), quotaRepository.balance);
    }

    @Test
    void directMembershipOrderActivatesMembershipAndIsIdempotent() {
        InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository(BigDecimal.ZERO);
        UserQuotaService service = serviceWithProduct(quotaRepository,
                membershipProduct("G1001", "Plus 会员", new BigDecimal("1000.00")));
        TradeOrderEntity order = order("O10001", TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.PAY_SUCCESS);

        service.grantQuotaForPaidOrder(order);
        service.grantQuotaForPaidOrder(order);

        assertEquals(1, quotaRepository.flows.size());
        assertEquals(0, quotaRepository.flows.get(0).getQuotaAmount().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0"), quotaRepository.balance);
        assertEquals("G1001", quotaRepository.membership.getPlanCode());
        assertEquals("Plus 会员", quotaRepository.membership.getPlanName());
        assertEquals(new BigDecimal("1000.00"), quotaRepository.membership.getMonthlyQuota());
        assertEquals(BigDecimal.ZERO, quotaRepository.membership.getMonthlyUsedQuota());
        assertTrue(quotaRepository.membership.isActive(LocalDateTime.now()));
        assertEquals(TradeOrderStatusEnumVO.DEAL_DONE, order.getOrderStatus());
    }

    private static UserQuotaService serviceWithProduct(InMemoryQuotaRepository quotaRepository, QuotaProduct product) {
        QuotaProductRepository QuotaProductRepository = mock(QuotaProductRepository.class);
        when(QuotaProductRepository.queryProductByGoodsId(product.getGoodsId())).thenReturn(Optional.of(product));
        return new UserQuotaService(quotaRepository, QuotaProductRepository, mock(TradeOrderRepository.class));
    }

    private static QuotaProduct quotaProduct(String goodsId, BigDecimal quotaAmount) {
        QuotaProduct product = new QuotaProduct();
        product.setGoodsId(goodsId);
        product.setQuotaAmount(quotaAmount);
        return product;
    }

    private static QuotaProduct membershipProduct(String goodsId, String goodsName, BigDecimal monthlyQuota) {
        QuotaProduct product = quotaProduct(goodsId, monthlyQuota);
        product.setGoodsName(goodsName);
        product.setProductType("MEMBERSHIP_PLAN");
        return product;
    }

    private static TradeOrderEntity order(String orderId,
                                          TradeBuyTypeEnumVO buyType,
                                          TradeOrderStatusEnumVO orderStatus) {
        TradeOrderEntity order = new TradeOrderEntity();
        order.setOrderId(orderId);
        order.setUserId("U10001");
        order.setGoodsId("G1001");
        order.setBuyType(buyType);
        order.setOrderStatus(orderStatus);
        order.setPayAmount(BigDecimal.ONE);
        return order;
    }

    private static UserMembershipAccount activeMembership(String userId, BigDecimal quota) {
        UserMembershipAccount membership = new UserMembershipAccount();
        membership.setUserId(userId);
        membership.setStatus("ACTIVE");
        membership.setMonthlyQuota(quota);
        membership.setMonthlyUsedQuota(BigDecimal.ZERO);
        membership.setCycleStartTime(LocalDateTime.now().minusDays(1));
        membership.setCycleEndTime(LocalDateTime.now().plusDays(1));
        return membership;
    }

    private static class InMemoryQuotaRepository implements UserQuotaRepository {
        private BigDecimal balance;
        private BigDecimal used = BigDecimal.ZERO;
        private UserMembershipAccount membership;
        private UserModelConfig modelConfig;
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

        @Override
        public Optional<UserMembershipAccount> queryMembership(String userId) {
            return Optional.ofNullable(membership);
        }

        @Override
        public void upsertMembership(UserMembershipAccount membership) {
            this.membership = membership;
        }

        @Override
        public int decreaseMembershipQuota(String userId, BigDecimal amount) {
            if (membership == null || membership.remainingQuota().compareTo(amount) < 0) {
                return 0;
            }
            membership.setMonthlyUsedQuota(membership.getMonthlyUsedQuota().add(amount));
            return 1;
        }

        @Override
        public Optional<UserModelConfig> queryModelConfig(String userId) {
            return Optional.ofNullable(modelConfig);
        }

        @Override
        public void upsertModelConfig(UserModelConfig modelConfig) {
            this.modelConfig = modelConfig;
        }
    }
}
















