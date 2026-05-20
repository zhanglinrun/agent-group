package com.linrun.trigger.service;

import com.linrun.api.payment.request.CreatePaymentRequest;
import com.linrun.api.payment.request.PaymentWebhookRequest;
import com.linrun.api.payment.request.ReconcilePaymentRequest;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.api.payment.response.CreatePaymentResponse;
import com.linrun.api.payment.response.PaymentWebhookResponse;
import com.linrun.api.payment.response.ReconcilePaymentResponse;
import com.linrun.api.payment.response.RefundPaymentResponse;
import com.linrun.domain.payment.adapter.PaymentGatewayClient;
import com.linrun.domain.payment.model.PaymentCreateCommand;
import com.linrun.domain.payment.model.PaymentCreateResult;
import com.linrun.domain.payment.model.PaymentReconcileCommand;
import com.linrun.domain.payment.model.PaymentReconcileResult;
import com.linrun.domain.payment.model.PaymentRefundCommand;
import com.linrun.domain.payment.model.PaymentRefundResult;
import com.linrun.domain.payment.model.PaymentWebhookCommand;
import com.linrun.domain.payment.model.PaymentWebhookResult;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.adapter.TradeStatusFlowRepository;
import com.linrun.domain.order.model.PayOrder;
import com.linrun.domain.order.model.PayStatus;
import com.linrun.domain.order.model.RefundOrder;
import com.linrun.domain.order.model.TradeBuyType;
import com.linrun.domain.order.model.TradeOrder;
import com.linrun.domain.order.model.TradeOrderStatus;
import com.linrun.domain.order.model.TradeStatusFlow;
import com.linrun.domain.order.service.TradeOrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

    @Test
    void shouldCreateGatewayPayment() {
        Fixture fixture = fixture(TradeOrderStatus.PAY_WAIT, PayStatus.WAIT_PAY);
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId("O10001");
        request.setPayChannel("MOCK_PAY");

        CreatePaymentResponse response = fixture.service.createPayment(request);

        assertEquals("mock://pay/P10001", response.getPayUrl());
        assertEquals("P10001", fixture.gateway.createCommand.getPayOrderId());
        assertTrue(fixture.flowRepository.flows.stream()
                .anyMatch(flow -> TradeStatusFlowService.EVENT_CREATE_GATEWAY_PAYMENT.equals(flow.getEventType())));
    }

    @Test
    void shouldVerifyWebhookAndMarkPaySuccess() {
        Fixture fixture = fixture(TradeOrderStatus.PAY_WAIT, PayStatus.WAIT_PAY);
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("MOCK_PAY");
        request.setOrderId("O10001");
        request.setPayOrderId("P10001");
        request.setGatewayTradeNo("GT10001");
        request.setPayTime(LocalDateTime.of(2026, 5, 14, 10, 0));

        PaymentWebhookResponse response = fixture.service.handleWebhook(request);

        assertTrue(response.isVerified());
        assertEquals(TradeOrderStatus.PAY_SUCCESS, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatus.SUCCESS, fixture.repository.payOrder.getPayStatus());
        assertEquals("GT10001", response.getGatewayTradeNo());
    }

    @Test
    void shouldRefundPaidOrderWithGateway() {
        Fixture fixture = fixture(TradeOrderStatus.PAY_SUCCESS, PayStatus.SUCCESS);
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId("O10001");
        request.setRefundReason("用户申请退款");

        RefundPaymentResponse response = fixture.service.refund(request);

        assertEquals(TradeOrderStatus.REFUNDED, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatus.REFUNDED, fixture.repository.payOrder.getPayStatus());
        assertNotNull(response.getRefundId());
        assertEquals("用户申请退款", fixture.repository.refundOrder.getRefundReason());
    }

    @Test
    void shouldReconcileLocalPaymentStatus() {
        Fixture fixture = fixture(TradeOrderStatus.PAY_SUCCESS, PayStatus.SUCCESS);
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setOrderId("O10001");
        request.setBillDate(LocalDate.of(2026, 5, 14));

        ReconcilePaymentResponse response = fixture.service.reconcile(request);

        assertTrue(response.isMatched());
        assertEquals("GT10001", response.getGatewayTradeNo());
        assertEquals(PayStatus.SUCCESS.name(), response.getLocalPayStatus());
    }

    private Fixture fixture(TradeOrderStatus orderStatus, PayStatus payStatus) {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(orderStatus, payStatus);
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        TradeStatusFlowService flowService = new TradeStatusFlowService(flowRepository);
        TradeOrderService tradeOrderService = new TradeOrderService();
        MockPayCallbackService callbackService = new MockPayCallbackService(
                repository,
                tradeOrderService,
                new GroupBuySettlementService(null, repository, flowService),
                flowService);
        FakePaymentGatewayClient gateway = new FakePaymentGatewayClient();
        return new Fixture(
                new PaymentService(repository, tradeOrderService, callbackService, gateway, flowService),
                repository,
                flowRepository,
                gateway);
    }

    private record Fixture(PaymentService service,
                           FakeTradeOrderRepository repository,
                           FakeTradeStatusFlowRepository flowRepository,
                           FakePaymentGatewayClient gateway) {
    }

    private static class FakePaymentGatewayClient implements PaymentGatewayClient {

        private PaymentCreateCommand createCommand;

        @Override
        public PaymentCreateResult createPayment(PaymentCreateCommand command) {
            this.createCommand = command;
            return PaymentCreateResult.created(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    command.getPayChannel(),
                    "mock://pay/" + command.getPayOrderId(),
                    "GT" + command.getPayOrderId(),
                    "created");
        }

        @Override
        public PaymentWebhookResult verifyWebhook(PaymentWebhookCommand command) {
            return PaymentWebhookResult.verified(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    command.getGatewayTradeNo(),
                    command.getPayTime(),
                    "verified");
        }

        @Override
        public PaymentRefundResult refund(PaymentRefundCommand command) {
            return PaymentRefundResult.success(command.getOrderId(), command.getPayOrderId(), "R10001", "refunded");
        }

        @Override
        public PaymentReconcileResult reconcile(PaymentReconcileCommand command) {
            return PaymentReconcileResult.matched(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    command.getGatewayTradeNo(),
                    "matched");
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private final TradeOrder tradeOrder;
        private final PayOrder payOrder;
        private RefundOrder refundOrder;

        private FakeTradeOrderRepository(TradeOrderStatus orderStatus, PayStatus payStatus) {
            tradeOrder = new TradeOrder();
            tradeOrder.setOrderId("O10001");
            tradeOrder.setUserId("U10001");
            tradeOrder.setGoodsId("G10001");
            tradeOrder.setGoodsName("轻薄学习平板标准版");
            tradeOrder.setBuyType(TradeBuyType.DIRECT);
            tradeOrder.setOriginAmount(new BigDecimal("2399.00"));
            tradeOrder.setPayAmount(new BigDecimal("2399.00"));
            tradeOrder.setOrderStatus(orderStatus);
            tradeOrder.setCreateTime(LocalDateTime.now());

            payOrder = PayOrder.waitPay(
                    "P10001",
                    "O10001",
                    new BigDecimal("2399.00"),
                    "MOCK_PAY",
                    "mock://pay/P10001",
                    LocalDateTime.now());
            payOrder.setPayStatus(payStatus);
        }

        @Override
        public void save(TradeOrder tradeOrder, PayOrder payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder.setOrderStatus(tradeOrder.getOrderStatus());
            this.tradeOrder.setPayTime(tradeOrder.getPayTime());
            this.payOrder.setPayStatus(payOrder.getPayStatus());
            this.payOrder.setOutTradeNo(payOrder.getOutTradeNo());
            this.payOrder.setPayTime(payOrder.getPayTime());
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
        }

        @Override
        public void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder) {
        }

        @Override
        public void saveRefundOrder(RefundOrder refundOrder) {
            this.refundOrder = refundOrder;
        }

        @Override
        public void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder) {
            this.tradeOrder.setOrderStatus(tradeOrder.getOrderStatus());
            this.payOrder.setPayStatus(payOrder.getPayStatus());
        }

        @Override
        public Optional<RefundOrder> queryRefundOrderByOrderId(String orderId) {
            return Optional.ofNullable(refundOrder);
        }

        @Override
        public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
            return Optional.of(tradeOrder);
        }

        @Override
        public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
            return Optional.of(payOrder);
        }
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlow> flows = new ArrayList<>();

        @Override
        public void save(TradeStatusFlow flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlow> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> flow.getOrderId().equals(orderId))
                    .toList();
        }
    }
}
