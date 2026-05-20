package com.linrun.trigger.service;

import com.linrun.api.order.request.MockPayCallbackRequest;
import com.linrun.api.order.response.MockPayCallbackResponse;
import com.linrun.domain.marketing.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.marketing.model.GroupBuyLockResult;
import com.linrun.domain.marketing.model.GroupBuyOrderLock;
import com.linrun.domain.marketing.model.GroupBuySettlementResult;
import com.linrun.domain.marketing.model.GroupBuyTeam;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.adapter.TradeStatusFlowRepository;
import com.linrun.domain.order.model.PayOrder;
import com.linrun.domain.order.model.PayStatus;
import com.linrun.domain.order.model.RefundOrder;
import com.linrun.domain.order.model.TradeStatusFlow;
import com.linrun.domain.order.model.TradeBuyType;
import com.linrun.domain.order.model.TradeOrder;
import com.linrun.domain.order.model.TradeOrderStatus;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPayCallbackServiceTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 5, 13, 16, 40, 0);
    private static final LocalDateTime PAY_TIME = LocalDateTime.of(2026, 5, 13, 16, 45, 0);

    @Test
    void shouldMarkOrderAndPayOrderSuccess() {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(waitPayOrder(), waitPay());
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        MockPayCallbackService service = service(repository, flowRepository);
        MockPayCallbackRequest request = request("O10001", "T10001", PAY_TIME);

        MockPayCallbackResponse response = service.paySuccess(request);

        assertEquals("O10001", response.getOrderId());
        assertEquals("P10001", response.getPayOrderId());
        assertEquals(TradeOrderStatus.PAY_SUCCESS.name(), response.getOrderStatus());
        assertEquals(PayStatus.SUCCESS.name(), response.getPayStatus());
        assertEquals("T10001", response.getOutTradeNo());
        assertEquals(PAY_TIME, response.getPayTime());
        assertEquals(TradeOrderStatus.PAY_SUCCESS, repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatus.SUCCESS, repository.payOrder.getPayStatus());
        assertEquals(1, repository.updateCount);
        assertEquals(2, flowRepository.flows.size());
        assertEquals(TradeStatusFlowService.EVENT_PAY_SUCCESS, flowRepository.flows.get(0).getEventType());
        assertEquals(TradeStatusFlowService.EVENT_PAY_SUCCESS, flowRepository.flows.get(1).getEventType());
    }

    @Test
    void shouldHandleRepeatedCallbackIdempotently() {
        TradeOrder tradeOrder = waitPayOrder();
        PayOrder payOrder = waitPay();
        tradeOrder.markPaySuccess(PAY_TIME);
        payOrder.markSuccess("T10001", PAY_TIME);
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(tradeOrder, payOrder);
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        MockPayCallbackService service = service(repository, flowRepository);

        MockPayCallbackResponse response = service.paySuccess(request("O10001", "T10002", PAY_TIME.plusMinutes(1)));

        assertEquals(TradeOrderStatus.PAY_SUCCESS.name(), response.getOrderStatus());
        assertEquals(PayStatus.SUCCESS.name(), response.getPayStatus());
        assertEquals("T10001", response.getOutTradeNo());
        assertEquals(PAY_TIME, response.getPayTime());
        assertEquals(1, repository.updateCount);
        assertEquals(0, flowRepository.flows.size());
    }

    @Test
    void shouldThrowWhenOrderMissing() {
        MockPayCallbackService service = service(new FakeTradeOrderRepository(null, null));

        AppException exception = assertThrows(AppException.class,
                () -> service.paySuccess(request("O404", "T10001", PAY_TIME)));

        assertEquals("TRADE_0013", exception.getCode());
        assertEquals("订单不存在", exception.getMessage());
    }

    @Test
    void shouldThrowWhenOutTradeNoIsBlank() {
        MockPayCallbackService service = service(new FakeTradeOrderRepository(waitPayOrder(), waitPay()));

        AppException exception = assertThrows(AppException.class,
                () -> service.paySuccess(request("O10001", " ", PAY_TIME)));

        assertEquals("0001", exception.getCode());
        assertEquals("外部交易单号不能为空", exception.getMessage());
    }

    private MockPayCallbackRequest request(String orderId, String outTradeNo, LocalDateTime payTime) {
        MockPayCallbackRequest request = new MockPayCallbackRequest();
        request.setOrderId(orderId);
        request.setOutTradeNo(outTradeNo);
        request.setPayTime(payTime);
        return request;
    }

    private MockPayCallbackService service(FakeTradeOrderRepository repository) {
        return service(repository, new FakeTradeStatusFlowRepository());
    }

    private MockPayCallbackService service(FakeTradeOrderRepository repository,
                                           FakeTradeStatusFlowRepository flowRepository) {
        TradeStatusFlowService tradeStatusFlowService = new TradeStatusFlowService(flowRepository);
        return new MockPayCallbackService(
                repository,
                new TradeOrderService(),
                new GroupBuySettlementService(new EmptyGroupBuyOrderLockRepository(), repository, tradeStatusFlowService),
                tradeStatusFlowService);
    }

    private TradeOrder waitPayOrder() {
        TradeOrder order = new TradeOrder();
        order.setOrderId("O10001");
        order.setUserId("U10001");
        order.setGoodsId("G10001");
        order.setGoodsName("轻薄学习平板标准版");
        order.setBuyType(TradeBuyType.DIRECT);
        order.setOriginAmount(new BigDecimal("2399.00"));
        order.setPayAmount(new BigDecimal("2399.00"));
        order.setOrderStatus(TradeOrderStatus.PAY_WAIT);
        order.setCreateTime(CREATE_TIME);
        return order;
    }

    private PayOrder waitPay() {
        return PayOrder.waitPay("P10001", "O10001", new BigDecimal("2399.00"),
                "MOCK_PAY", "mock://MOCK_PAY/O10001", CREATE_TIME);
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private TradeOrder tradeOrder;
        private PayOrder payOrder;
        private RefundOrder refundOrder;
        private int updateCount;

        private FakeTradeOrderRepository(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder = tradeOrder;
            this.payOrder = payOrder;
        }

        @Override
        public void save(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder = tradeOrder;
            this.payOrder = payOrder;
        }

        @Override
        public void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder = tradeOrder;
            this.payOrder = payOrder;
            this.updateCount++;
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
            if (tradeOrder != null && orderIds.contains(tradeOrder.getOrderId())) {
                tradeOrder.markGroupSettled();
            }
        }

        @Override
        public void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder = tradeOrder;
            this.payOrder = payOrder;
        }

        @Override
        public void saveRefundOrder(RefundOrder refundOrder) {
            this.refundOrder = refundOrder;
        }

        @Override
        public void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder = tradeOrder;
            this.payOrder = payOrder;
        }

        @Override
        public Optional<RefundOrder> queryRefundOrderByOrderId(String orderId) {
            return Optional.ofNullable(refundOrder);
        }

        @Override
        public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
            return Optional.ofNullable(tradeOrder);
        }

        @Override
        public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
            return Optional.ofNullable(payOrder);
        }
    }

    private static class EmptyGroupBuyOrderLockRepository implements GroupBuyOrderLockRepository {

        @Override
        public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
            return Optional.empty();
        }

        @Override
        public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
            return Optional.empty();
        }

        @Override
        public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public GroupBuySettlementResult settlePaidOrder(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> queryPaidOrderIdsByTeamId(String teamId) {
            return List.of();
        }

        @Override
        public GroupBuySettlementResult releaseLockedOrder(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupBuySettlementResult releasePaidOrder(String orderId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlow> flows = new java.util.ArrayList<>();

        @Override
        public void save(TradeStatusFlow flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlow> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> orderId.equals(flow.getOrderId()))
                    .toList();
        }
    }
}
