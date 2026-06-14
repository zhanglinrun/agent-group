package com.linrun.domain.trade.service;

import com.linrun.api.dto.TradeConsistencyCheckRequest;
import com.linrun.api.dto.TradeConsistencyCheckResponse;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.RefundStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeConsistencyCheckServiceTest {

    @Test
    void shouldDetectGroupOrderWaitingSettlement() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.PAY_SUCCESS),
                pay(PayStatusEnumVO.SUCCESS));

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.WAIT_GROUP_SETTLEMENT, item.getConclusion());
        assertFalse(item.isQuotaGrantAllowed());
        assertEquals("等待成团", item.getSettlementLabel());
        assertEquals("拼团支付成功只表示名额已支付，暂不能发放额度。", item.getSettlementDetail());
        assertTrue(item.getFacts().contains("额度到账流水：缺失"));
    }

    @Test
    void shouldDetectQuotaGrantRequired() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.PAY_SUCCESS),
                pay(PayStatusEnumVO.SUCCESS));

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.QUOTA_GRANT_REQUIRED, item.getConclusion());
        assertTrue(item.isQuotaGrantAllowed());
        assertEquals("待发放额度", item.getSettlementLabel());
        assertEquals("G10001", item.getGoodsId());
        assertEquals("quota package", item.getGoodsName());
        assertEquals(new BigDecimal("19.90"), item.getOrderPayAmount());
    }

    @Test
    void shouldAllowQuotaGrantAfterGroupSettled() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.GROUP_SETTLED),
                pay(PayStatusEnumVO.SUCCESS));

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.QUOTA_GRANT_REQUIRED, item.getConclusion());
        assertTrue(item.isQuotaGrantAllowed());
        assertEquals("待发放额度", item.getSettlementLabel());
    }

    @Test
    void shouldDetectGroupSettledGrantedConsistent() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.GROUP_SETTLED),
                pay(PayStatusEnumVO.SUCCESS));
        fixture.quotaRepository.addFlow("U10001", UserQuotaService.FLOW_ORDER_GRANT, "O10001");

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.QUOTA_GRANTED_CONSISTENT, item.getConclusion());
        assertTrue(item.isQuotaGrantAllowed());
        assertEquals("额度已到账", item.getSettlementLabel());
        assertTrue(item.isQuotaGrantFlowExists());
    }

    @Test
    void shouldDetectGrantedConsistent() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.DEAL_DONE),
                pay(PayStatusEnumVO.SUCCESS));
        fixture.quotaRepository.addFlow("U10001", UserQuotaService.FLOW_ORDER_GRANT, "O10001");

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.QUOTA_GRANTED_CONSISTENT, item.getConclusion());
        assertTrue(item.isQuotaGrantAllowed());
        assertEquals("额度已到账", item.getSettlementLabel());
        assertTrue(item.isQuotaGrantFlowExists());
    }

    @Test
    void shouldDetectRefundRollbackRequired() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.REFUNDED),
                pay(PayStatusEnumVO.REFUNDED));
        fixture.repository.refundOrder = refund();
        fixture.quotaRepository.addFlow("U10001", UserQuotaService.FLOW_ORDER_GRANT, "O10001");

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.REFUND_ROLLBACK_REQUIRED, item.getConclusion());
        assertTrue(item.isRefundRollbackRequired());
        assertFalse(item.isQuotaGrantAllowed());
        assertEquals("待回滚", item.getSettlementLabel());
        assertEquals(new BigDecimal("19.90"), item.getRefundAmount());
        assertTrue(item.getFacts().contains("退款回滚流水：缺失"));
    }

    @Test
    void shouldDetectTradeStateConflict() {
        Fixture fixture = fixture(order(TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.PAY_WAIT),
                pay(PayStatusEnumVO.SUCCESS));

        TradeConsistencyCheckResponse.Item item = fixture.check("O10001");

        assertEquals(TradeConsistencyCheckService.TRADE_STATE_CONFLICT, item.getConclusion());
        assertFalse(item.isQuotaGrantAllowed());
        assertEquals("状态冲突", item.getSettlementLabel());
        assertTrue(item.getFacts().contains("支付：SUCCESS/ALIPAY/19.90"));
    }

    private static Fixture fixture(TradeOrderEntity order, PayOrderEntity payOrder) {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(order, payOrder);
        FakeUserQuotaRepository quotaRepository = new FakeUserQuotaRepository();
        return new Fixture(new TradeConsistencyCheckService(repository, quotaRepository), repository, quotaRepository);
    }

    private static TradeOrderEntity order(TradeBuyTypeEnumVO buyType, TradeOrderStatusEnumVO status) {
        TradeOrderEntity order = new TradeOrderEntity();
        order.setId(1L);
        order.setOrderId("O10001");
        order.setUserId("U10001");
        order.setGoodsId("G10001");
        order.setGoodsName("quota package");
        order.setActivityId("A10001");
        order.setBuyType(buyType);
        order.setOriginAmount(new BigDecimal("19.90"));
        order.setPayAmount(new BigDecimal("19.90"));
        order.setOrderStatus(status);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    private static PayOrderEntity pay(PayStatusEnumVO status) {
        PayOrderEntity payOrder = PayOrderEntity.waitPay(
                "P10001",
                "O10001",
                new BigDecimal("19.90"),
                "ALIPAY",
                "",
                LocalDateTime.now());
        payOrder.setPayStatus(status);
        return payOrder;
    }

    private static RefundOrderEntity refund() {
        RefundOrderEntity refundOrder = new RefundOrderEntity();
        refundOrder.setRefundId("R10001");
        refundOrder.setOrderId("O10001");
        refundOrder.setPayOrderId("P10001");
        refundOrder.setUserId("U10001");
        refundOrder.setRefundAmount(new BigDecimal("19.90"));
        refundOrder.setRefundStatus(RefundStatusEnumVO.SUCCESS);
        refundOrder.setRefundTime(LocalDateTime.now());
        return refundOrder;
    }

    private record Fixture(TradeConsistencyCheckService service,
                           FakeTradeOrderRepository repository,
                           FakeUserQuotaRepository quotaRepository) {

        private TradeConsistencyCheckResponse.Item check(String orderId) {
            TradeConsistencyCheckRequest request = new TradeConsistencyCheckRequest();
            request.setOrderId(orderId);
            return service.check(request).getItems().getFirst();
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private final TradeOrderEntity order;
        private final PayOrderEntity payOrder;
        private RefundOrderEntity refundOrder;

        private FakeTradeOrderRepository(TradeOrderEntity order, PayOrderEntity payOrder) {
            this.order = order;
            this.payOrder = payOrder;
        }

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
        }

        @Override
        public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void saveRefundOrder(RefundOrderEntity refundOrder) {
        }

        @Override
        public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
            return Optional.ofNullable(refundOrder);
        }

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            return Optional.ofNullable(order);
        }

        @Override
        public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
            return Optional.ofNullable(payOrder);
        }

        @Override
        public List<TradeOrderEntity> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
            return List.of(order);
        }

        @Override
        public List<TradeOrderEntity> queryTradeOrders(Long lastId, int pageSize, Integer marketType,
                                                       String orderStatus, String keyword) {
            return List.of(order);
        }
    }

    private static class FakeUserQuotaRepository implements UserQuotaRepository {

        private final Map<String, UserQuotaFlow> flows = new HashMap<>();

        private void addFlow(String userId, String flowType, String bizId) {
            UserQuotaFlow flow = new UserQuotaFlow();
            flow.setUserId(userId);
            flow.setFlowType(flowType);
            flow.setBizId(bizId);
            flows.put(key(userId, flowType, bizId), flow);
        }

        @Override
        public void createAccountIfAbsent(String userId) {
        }

        @Override
        public Optional<UserQuotaAccount> queryAccount(String userId) {
            return Optional.empty();
        }

        @Override
        public int increaseQuota(String userId, BigDecimal amount) {
            return 0;
        }

        @Override
        public int decreaseQuota(String userId, BigDecimal amount) {
            return 0;
        }

        @Override
        public int decreaseQuotaAllowNegative(String userId, BigDecimal amount) {
            return 0;
        }

        @Override
        public void saveFlow(UserQuotaFlow flow) {
        }

        @Override
        public Optional<UserQuotaFlow> queryFlow(String userId, String flowType, String bizId) {
            return Optional.ofNullable(flows.get(key(userId, flowType, bizId)));
        }

        @Override
        public List<UserQuotaFlow> queryRecentFlows(String userId, int limit) {
            return List.of();
        }

        @Override
        public void saveUsage(ModelUsageRecord usageRecord) {
        }

        @Override
        public Optional<UserMembershipAccount> queryMembership(String userId) {
            return Optional.empty();
        }

        @Override
        public void upsertMembership(UserMembershipAccount membership) {
        }

        @Override
        public int decreaseMembershipQuota(String userId, BigDecimal amount) {
            return 0;
        }

        @Override
        public Optional<UserModelConfig> queryModelConfig(String userId) {
            return Optional.empty();
        }

        @Override
        public void upsertModelConfig(UserModelConfig modelConfig) {
        }

        private String key(String userId, String flowType, String bizId) {
            return userId + "|" + flowType + "|" + bizId;
        }
    }
}














