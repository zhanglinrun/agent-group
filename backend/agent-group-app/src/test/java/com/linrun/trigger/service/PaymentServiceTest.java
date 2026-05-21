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
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.order.model.entity.TradeStatusFlowEntity;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.trigger.config.MockPaymentAccessChecker;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

    @Test
    void shouldCreateGatewayPayment() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
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
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("MOCK_PAY");
        request.setOrderId("O10001");
        request.setPayOrderId("P10001");
        request.setGatewayTradeNo("GT10001");
        request.setPayTime(LocalDateTime.of(2026, 5, 14, 10, 0));

        PaymentWebhookResponse response = fixture.service.handleWebhook(request);

        assertTrue(response.isVerified());
        assertEquals(TradeOrderStatusEnumVO.PAY_SUCCESS, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.SUCCESS, fixture.repository.payOrder.getPayStatus());
        assertEquals("GT10001", response.getGatewayTradeNo());
    }

    @Test
    void shouldRejectRealWebhookWhenPayOrderMismatches() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.gateway.webhookPayOrderId = "P20001";
        fixture.gateway.webhookGatewayTradeNo = "GT10001";
        fixture.gateway.webhookAmount = new BigDecimal("2399.00");
        fixture.gateway.webhookTradeStatus = "TRADE_SUCCESS";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.handleWebhook(request));

        assertEquals("PAY_0010", exception.getCode());
        assertEquals(PayStatusEnumVO.WAIT_PAY, fixture.repository.payOrder.getPayStatus());
    }

    @Test
    void shouldRejectRealWebhookWhenAmountMismatches() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.gateway.webhookPayOrderId = "P10001";
        fixture.gateway.webhookGatewayTradeNo = "GT10001";
        fixture.gateway.webhookAmount = new BigDecimal("1.00");
        fixture.gateway.webhookTradeStatus = "TRADE_SUCCESS";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.handleWebhook(request));

        assertEquals("PAY_0012", exception.getCode());
        assertEquals(PayStatusEnumVO.WAIT_PAY, fixture.repository.payOrder.getPayStatus());
    }

    @Test
    void shouldRejectRealWebhookWhenTradeStatusIsNotSuccess() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.gateway.webhookPayOrderId = "P10001";
        fixture.gateway.webhookGatewayTradeNo = "GT10001";
        fixture.gateway.webhookAmount = new BigDecimal("2399.00");
        fixture.gateway.webhookTradeStatus = "WAIT_BUYER_PAY";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.handleWebhook(request));

        assertEquals("PAY_0014", exception.getCode());
        assertEquals(PayStatusEnumVO.WAIT_PAY, fixture.repository.payOrder.getPayStatus());
    }

    @Test
    void shouldRefundPaidOrderWithGateway() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId("O10001");
        request.setRefundReason("用户申请退款");

        RefundPaymentResponse response = fixture.service.refund(request);

        assertEquals(TradeOrderStatusEnumVO.REFUNDED, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.REFUNDED, fixture.repository.payOrder.getPayStatus());
        assertNotNull(response.getRefundId());
        assertEquals("用户申请退款", fixture.repository.refundOrder.getRefundReason());
    }

    @Test
    void shouldReconcileLocalPaymentStatus() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setOrderId("O10001");
        request.setBillDate(LocalDate.of(2026, 5, 14));

        ReconcilePaymentResponse response = fixture.service.reconcile(request);

        assertTrue(response.isMatched());
        assertEquals("GT10001", response.getGatewayTradeNo());
        assertEquals(PayStatusEnumVO.SUCCESS.name(), response.getLocalPayStatus());
    }

    private Fixture fixture(TradeOrderStatusEnumVO orderStatus, PayStatusEnumVO payStatus) {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(orderStatus, payStatus);
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        TradeStatusFlowService flowService = new TradeStatusFlowService(flowRepository);
        TradeOrderService tradeOrderService = new TradeOrderService();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        MockPayCallbackService callbackService = new MockPayCallbackService(
                repository,
                tradeOrderService,
                new GroupBuySettlementService(null, repository, flowService),
                flowService);
        FakePaymentGatewayClient gateway = new FakePaymentGatewayClient();
        return new Fixture(
                new PaymentService(repository, tradeOrderService, callbackService,
                        new MockPaymentAccessChecker(environment), gateway,
                        new PaymentWebhookReplayGuard(300L), flowService),
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
        private String webhookPayOrderId;
        private String webhookGatewayTradeNo;
        private BigDecimal webhookAmount;
        private String webhookTradeStatus;

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
                    webhookPayOrderId == null ? command.getPayOrderId() : webhookPayOrderId,
                    webhookGatewayTradeNo == null ? command.getGatewayTradeNo() : webhookGatewayTradeNo,
                    command.getPayTime(),
                    "EVT10001",
                    LocalDateTime.now(),
                    webhookAmount == null ? command.getPayAmount() : webhookAmount,
                    webhookTradeStatus == null ? command.getTradeStatus() : webhookTradeStatus,
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

        private final TradeOrderEntity tradeOrder;
        private final PayOrderEntity payOrder;
        private RefundOrderEntity refundOrder;

        private FakeTradeOrderRepository(TradeOrderStatusEnumVO orderStatus, PayStatusEnumVO payStatus) {
            tradeOrder = new TradeOrderEntity();
            tradeOrder.setOrderId("O10001");
            tradeOrder.setUserId("U10001");
            tradeOrder.setGoodsId("G10001");
            tradeOrder.setGoodsName("轻薄学习平板标准版");
            tradeOrder.setBuyType(TradeBuyTypeEnumVO.DIRECT);
            tradeOrder.setOriginAmount(new BigDecimal("2399.00"));
            tradeOrder.setPayAmount(new BigDecimal("2399.00"));
            tradeOrder.setOrderStatus(orderStatus);
            tradeOrder.setCreateTime(LocalDateTime.now());

            payOrder = PayOrderEntity.waitPay(
                    "P10001",
                    "O10001",
                    new BigDecimal("2399.00"),
                    "MOCK_PAY",
                    "mock://pay/P10001",
                    LocalDateTime.now());
            payOrder.setPayStatus(payStatus);
        }

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
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
        public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void saveRefundOrder(RefundOrderEntity refundOrder) {
            this.refundOrder = refundOrder;
        }

        @Override
        public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            this.tradeOrder.setOrderStatus(tradeOrder.getOrderStatus());
            this.payOrder.setPayStatus(payOrder.getPayStatus());
        }

        @Override
        public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
            return Optional.ofNullable(refundOrder);
        }

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            return Optional.of(tradeOrder);
        }

        @Override
        public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
            return Optional.of(payOrder);
        }
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlowEntity> flows = new ArrayList<>();

        @Override
        public void save(TradeStatusFlowEntity flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlowEntity> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> flow.getOrderId().equals(orderId))
                    .toList();
        }
    }
}
