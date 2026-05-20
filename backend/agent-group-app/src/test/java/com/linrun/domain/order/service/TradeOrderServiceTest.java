package com.linrun.domain.order.service;

import com.linrun.domain.order.model.CreateTradeOrderCommand;
import com.linrun.domain.order.model.PayOrder;
import com.linrun.domain.order.model.PayStatus;
import com.linrun.domain.order.model.TradeBuyType;
import com.linrun.domain.order.model.TradeOrder;
import com.linrun.domain.order.model.TradeOrderStatus;
import com.linrun.domain.order.model.TradePayOrder;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeOrderServiceTest {

    private static final LocalDateTime PAY_TIME = LocalDateTime.of(2026, 5, 13, 16, 30, 0);

    @Test
    void shouldCreateDirectOrderAndPayOrder() {
        TradeOrderService service = new TradeOrderService();

        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");

        assertTrue(order.getOrderId().startsWith("O"));
        assertEquals("U10001", order.getUserId());
        assertEquals(TradeBuyType.DIRECT, order.getBuyType());
        assertEquals(TradeOrderStatus.PAY_WAIT, tradePayOrder.getTradeOrder().getOrderStatus());
        assertEquals(new BigDecimal("2399.00"), tradePayOrder.getTradeOrder().getPayAmount());

        PayOrder payOrder = tradePayOrder.getPayOrder();
        assertTrue(payOrder.getPayOrderId().startsWith("P"));
        assertEquals(order.getOrderId(), payOrder.getOrderId());
        assertEquals(PayStatus.WAIT_PAY, payOrder.getPayStatus());
        assertEquals("MOCK_PAY", payOrder.getPayChannel());
        assertTrue(payOrder.getPayUrl().contains(order.getOrderId()));
    }

    @Test
    void shouldCreateGroupBuyOrderWithActivity() {
        TradeOrderService service = new TradeOrderService();

        TradeOrder order = service.createOrder(command(TradeBuyType.GROUP_BUY, "A10001", "2399.00", "2099.00"));

        assertEquals(TradeBuyType.GROUP_BUY, order.getBuyType());
        assertEquals("A10001", order.getActivityId());
        assertEquals(new BigDecimal("2399.00"), order.getOriginAmount());
        assertEquals(new BigDecimal("2099.00"), order.getPayAmount());
        assertEquals(TradeOrderStatus.CREATE, order.getOrderStatus());
        assertNotNull(order.getCreateTime());
    }

    @Test
    void shouldRejectGroupBuyOrderWithoutActivity() {
        TradeOrderService service = new TradeOrderService();

        AppException exception = assertThrows(AppException.class,
                () -> service.createOrder(command(TradeBuyType.GROUP_BUY, null, "2399.00", "2099.00")));

        assertEquals("0001", exception.getCode());
        assertEquals("拼团订单活动编号不能为空", exception.getMessage());
    }

    @Test
    void shouldRejectPayAmountGreaterThanOriginAmount() {
        TradeOrderService service = new TradeOrderService();

        AppException exception = assertThrows(AppException.class,
                () -> service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2499.00")));

        assertEquals("TRADE_0001", exception.getCode());
    }

    @Test
    void shouldMarkPaySuccessIdempotently() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");

        service.markPaySuccess(order, tradePayOrder.getPayOrder(), "T10001", PAY_TIME);
        service.markPaySuccess(order, tradePayOrder.getPayOrder(), "T10001", PAY_TIME.plusMinutes(1));

        assertEquals(TradeOrderStatus.PAY_SUCCESS, order.getOrderStatus());
        assertEquals(PayStatus.SUCCESS, tradePayOrder.getPayOrder().getPayStatus());
        assertEquals("T10001", tradePayOrder.getPayOrder().getOutTradeNo());
        assertEquals(PAY_TIME, tradePayOrder.getPayOrder().getPayTime());
        assertEquals(PAY_TIME, order.getPayTime());
    }

    @Test
    void shouldCloseUnpaidOrder() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");
        LocalDateTime closeTime = PAY_TIME.plusMinutes(30);

        service.closeUnpaidOrder(order, tradePayOrder.getPayOrder(), closeTime);

        assertEquals(TradeOrderStatus.CLOSED, order.getOrderStatus());
        assertEquals(PayStatus.CLOSED, tradePayOrder.getPayOrder().getPayStatus());
        assertEquals(closeTime, order.getCloseTime());
    }

    @Test
    void shouldRejectClosingPaidOrder() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");
        service.markPaySuccess(order, tradePayOrder.getPayOrder(), "T10001", PAY_TIME);

        AppException exception = assertThrows(AppException.class,
                () -> service.closeUnpaidOrder(order, tradePayOrder.getPayOrder(), PAY_TIME.plusMinutes(30)));

        assertEquals("TRADE_0011", exception.getCode());
    }

    @Test
    void shouldSettlePaidGroupBuyOrder() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.GROUP_BUY, "A10001", "2399.00", "2099.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");
        service.markPaySuccess(order, tradePayOrder.getPayOrder(), "T10001", PAY_TIME);

        order.markGroupSettled();
        order.markDealDone();

        assertEquals(TradeOrderStatus.DEAL_DONE, order.getOrderStatus());
    }

    @Test
    void shouldRejectGroupSettlementForDirectOrder() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        TradePayOrder tradePayOrder = service.createPayOrder(order, "MOCK_PAY");
        service.markPaySuccess(order, tradePayOrder.getPayOrder(), "T10001", PAY_TIME);

        AppException exception = assertThrows(AppException.class, order::markGroupSettled);

        assertEquals("TRADE_0008", exception.getCode());
    }

    @Test
    void shouldRejectMismatchedPayOrder() {
        TradeOrderService service = new TradeOrderService();
        TradeOrder order = service.createOrder(command(TradeBuyType.DIRECT, null, "2399.00", "2399.00"));
        PayOrder payOrder = PayOrder.waitPay("P10001", "OTHER_ORDER", new BigDecimal("2399.00"),
                "MOCK_PAY", "mock://pay", PAY_TIME);

        AppException exception = assertThrows(AppException.class,
                () -> service.markPaySuccess(order, payOrder, "T10001", PAY_TIME));

        assertEquals("TRADE_0012", exception.getCode());
    }

    private CreateTradeOrderCommand command(TradeBuyType buyType, String activityId, String originAmount, String payAmount) {
        CreateTradeOrderCommand command = new CreateTradeOrderCommand();
        command.setUserId("U10001");
        command.setGoodsId("G10001");
        command.setGoodsName("轻薄学习平板标准版");
        command.setActivityId(activityId);
        command.setBuyType(buyType);
        command.setOriginAmount(new BigDecimal(originAmount));
        command.setPayAmount(new BigDecimal(payAmount));
        return command;
    }
}
