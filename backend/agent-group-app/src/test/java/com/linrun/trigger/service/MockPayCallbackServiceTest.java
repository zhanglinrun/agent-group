package com.linrun.trigger.service;

import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.api.trade.response.MockPayCallbackResponse;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.PayStatus;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.model.TradeOrderStatus;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPayCallbackServiceTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 5, 13, 16, 40, 0);
    private static final LocalDateTime PAY_TIME = LocalDateTime.of(2026, 5, 13, 16, 45, 0);

    @Test
    void shouldMarkOrderAndPayOrderSuccess() {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(waitPayOrder(), waitPay());
        MockPayCallbackService service = new MockPayCallbackService(repository, new TradeOrderService());
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
    }

    @Test
    void shouldHandleRepeatedCallbackIdempotently() {
        TradeOrder tradeOrder = waitPayOrder();
        PayOrder payOrder = waitPay();
        tradeOrder.markPaySuccess(PAY_TIME);
        payOrder.markSuccess("T10001", PAY_TIME);
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(tradeOrder, payOrder);
        MockPayCallbackService service = new MockPayCallbackService(repository, new TradeOrderService());

        MockPayCallbackResponse response = service.paySuccess(request("O10001", "T10002", PAY_TIME.plusMinutes(1)));

        assertEquals(TradeOrderStatus.PAY_SUCCESS.name(), response.getOrderStatus());
        assertEquals(PayStatus.SUCCESS.name(), response.getPayStatus());
        assertEquals("T10001", response.getOutTradeNo());
        assertEquals(PAY_TIME, response.getPayTime());
        assertEquals(1, repository.updateCount);
    }

    @Test
    void shouldThrowWhenOrderMissing() {
        MockPayCallbackService service = new MockPayCallbackService(
                new FakeTradeOrderRepository(null, null),
                new TradeOrderService());

        AppException exception = assertThrows(AppException.class,
                () -> service.paySuccess(request("O404", "T10001", PAY_TIME)));

        assertEquals("TRADE_0013", exception.getCode());
        assertEquals("订单不存在", exception.getMessage());
    }

    @Test
    void shouldThrowWhenOutTradeNoIsBlank() {
        MockPayCallbackService service = new MockPayCallbackService(
                new FakeTradeOrderRepository(waitPayOrder(), waitPay()),
                new TradeOrderService());

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
        public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
            return Optional.ofNullable(tradeOrder);
        }

        @Override
        public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
            return Optional.ofNullable(payOrder);
        }
    }
}
