package com.linrun.domain.trade.service.payment;







import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.QueryPaymentRefundRequest;
import com.linrun.api.dto.QueryPaymentRefundResponse;
import com.linrun.api.dto.ReconcilePaymentRequest;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.api.dto.PaymentWebhookResponse;
import com.linrun.api.dto.ReconcilePaymentResponse;
import com.linrun.api.dto.RefundPaymentResponse;
import com.linrun.domain.market.service.GroupBuySettlementService;
import com.linrun.domain.trade.adapter.port.PaymentGatewayClient;
import com.linrun.domain.trade.adapter.repository.PaymentWebhookReplayRepository;
import com.linrun.domain.trade.model.payment.PaymentCompletionCommand;
import com.linrun.domain.trade.model.payment.PaymentCompletionResult;
import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryResult;
import com.linrun.domain.trade.model.payment.PaymentRefundResult;
import com.linrun.domain.trade.model.payment.PaymentWebhookCommand;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        request.setPayChannel("ALIPAY");

        CreatePaymentResponse response = fixture.service.createPayment(request);

        assertEquals("https://pay.example.com/P10001", response.getPayUrl());
        assertEquals("P10001", fixture.gateway.createCommand.getPayOrderId());
        assertTrue(fixture.flowRepository.flows.stream()
                .anyMatch(flow -> TradeStatusFlowService.EVENT_CREATE_GATEWAY_PAYMENT.equals(flow.getEventType())));
    }

    @Test
    void shouldRejectGatewayPaymentWhenOrderOwnerMismatches() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId("O10001");
        request.setPayChannel("ALIPAY");

        AppException exception = assertThrows(AppException.class,
                () -> fixture.service.createPayment(request, "U20001"));

        assertEquals("TRADE_0016", exception.getCode());
    }

    @Test
    void shouldVerifyWebhookAndMarkPaySuccess() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");
        request.setPayOrderId("P10001");
        request.setGatewayTradeNo("GT10001");
        request.setPayAmount(new BigDecimal("2399.00"));
        request.setTradeStatus("TRADE_SUCCESS");
        request.setPayTime(LocalDateTime.of(2026, 5, 14, 10, 0));

        PaymentWebhookResponse response = fixture.service.handleWebhook(request);

        assertTrue(response.isVerified());
        assertEquals(TradeOrderStatusEnumVO.PAY_SUCCESS, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.SUCCESS, fixture.repository.payOrder.getPayStatus());
        assertEquals("GT10001", response.getGatewayTradeNo());
    }

    @Test
    void shouldReleaseWebhookProcessingLockWhenHandlingFailsAndAllowRetry() {
        FakeReplayRepository replayRepository = new FakeReplayRepository();
        Fixture fixture = fixture(
                TradeOrderStatusEnumVO.PAY_WAIT,
                PayStatusEnumVO.WAIT_PAY,
                new PaymentWebhookReplayGuard(300L, replayRepository));
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.failNextUpdatePaySuccess = true;
        fixture.gateway.webhookPayOrderId = "P10001";
        fixture.gateway.webhookGatewayTradeNo = "GT10001";
        fixture.gateway.webhookAmount = new BigDecimal("2399.00");
        fixture.gateway.webhookTradeStatus = "TRADE_SUCCESS";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");
        request.setPayOrderId("P10001");
        request.setGatewayTradeNo("GT10001");
        request.setPayAmount(new BigDecimal("2399.00"));
        request.setTradeStatus("TRADE_SUCCESS");
        request.setPayTime(LocalDateTime.of(2026, 5, 14, 10, 0));

        AppException exception = assertThrows(AppException.class, () -> fixture.service.handleWebhook(request));

        assertEquals("TEST_0001", exception.getCode());
        assertEquals(TradeOrderStatusEnumVO.PAY_WAIT, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.WAIT_PAY, fixture.repository.payOrder.getPayStatus());
        assertEquals(1, fixture.repository.updatePaySuccessCount);
        assertTrue(replayRepository.keys.isEmpty());

        PaymentWebhookResponse response = fixture.service.handleWebhook(request);

        assertTrue(response.isVerified());
        assertEquals(TradeOrderStatusEnumVO.PAY_SUCCESS, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.SUCCESS, fixture.repository.payOrder.getPayStatus());
        assertEquals(2, fixture.repository.updatePaySuccessCount);
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
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId("O10001");
        request.setRefundReason("用户申请退款");

        RefundPaymentResponse response = fixture.service.refund(request);

        assertEquals(TradeOrderStatusEnumVO.REFUNDED, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.REFUNDED, fixture.repository.payOrder.getPayStatus());
        assertEquals("RO10001", response.getRefundId());
        assertEquals("RO10001", fixture.gateway.refundCommand.getRefundId());
        assertEquals("用户申请退款", fixture.repository.refundOrder.getRefundReason());
    }

    @Test
    void shouldRejectRefundBeforeGatewayWhenOrderIsNotRefundable() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.refund(request));

        assertEquals("TRADE_0015", exception.getCode());
        assertEquals(0, fixture.gateway.refundCallCount);
        assertEquals(null, fixture.repository.refundOrder);
    }

    @Test
    void shouldRejectRefundBeforeGatewayWhenGatewayTradeNoMissing() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.refund(request));

        assertEquals("PAY_0018", exception.getCode());
        assertEquals(0, fixture.gateway.refundCallCount);
        assertEquals(null, fixture.repository.refundOrder);
    }

    @Test
    void shouldApplySuccessfulRefundQueryToLocalState() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        fixture.gateway.refundQueryStatus = "SUCCESS";
        fixture.gateway.refundQueryRefundId = "RO10001";
        fixture.gateway.refundQueryAmount = new BigDecimal("2399.00");
        QueryPaymentRefundRequest request = new QueryPaymentRefundRequest();
        request.setOrderId("O10001");

        QueryPaymentRefundResponse response = fixture.service.queryRefund(request);

        assertTrue(response.isVerified());
        assertEquals("SUCCESS", response.getRefundStatus());
        assertEquals(TradeOrderStatusEnumVO.REFUNDED, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.REFUNDED, fixture.repository.payOrder.getPayStatus());
        assertEquals("RO10001", fixture.repository.refundOrder.getRefundId());
        assertTrue(fixture.flowRepository.flows.stream()
                .anyMatch(flow -> TradeStatusFlowService.EVENT_REFUND_SUCCESS.equals(flow.getEventType())));
    }

    @Test
    void shouldApplySuccessfulRefundWebhookToLocalState() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        fixture.gateway.refundWebhookStatus = "SUCCESS";
        fixture.gateway.refundWebhookRefundId = "RO10001";
        fixture.gateway.refundWebhookAmount = new BigDecimal("2399.00");
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");
        request.setPayOrderId("P10001");
        request.setGatewayTradeNo("GT10001");

        QueryPaymentRefundResponse response = fixture.service.handleRefundWebhook(request);

        assertTrue(response.isVerified());
        assertEquals("SUCCESS", response.getRefundStatus());
        assertEquals(TradeOrderStatusEnumVO.REFUNDED, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.REFUNDED, fixture.repository.payOrder.getPayStatus());
        assertEquals("RO10001", fixture.repository.refundOrder.getRefundId());
    }

    @Test
    void shouldRejectDuplicateSuccessWebhookWhenGatewayTradeNoMismatches() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        fixture.gateway.webhookPayOrderId = "P10001";
        fixture.gateway.webhookGatewayTradeNo = "GT20001";
        fixture.gateway.webhookAmount = new BigDecimal("2399.00");
        fixture.gateway.webhookTradeStatus = "TRADE_SUCCESS";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");

        AppException exception = assertThrows(AppException.class, () -> fixture.service.handleWebhook(request));

        assertEquals("PAY_0015", exception.getCode());
        assertEquals(0, fixture.paymentCompletionService.completeCount);
    }

    @Test
    void shouldRunSuccessPostProcessingForDuplicateWebhook() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        fixture.gateway.webhookPayOrderId = "P10001";
        fixture.gateway.webhookGatewayTradeNo = "GT10001";
        fixture.gateway.webhookAmount = new BigDecimal("2399.00");
        fixture.gateway.webhookTradeStatus = "TRADE_SUCCESS";
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");

        PaymentWebhookResponse response = fixture.service.handleWebhook(request);

        assertTrue(response.isVerified());
        assertEquals(1, fixture.paymentCompletionService.completeCount);
        assertEquals(0, fixture.repository.updatePaySuccessCount);
    }

    @Test
    void shouldReconcileLocalPaymentStatus() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_SUCCESS, PayStatusEnumVO.SUCCESS);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.repository.payOrder.setOutTradeNo("GT10001");
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setOrderId("O10001");
        request.setBillDate(LocalDate.of(2026, 5, 14));

        ReconcilePaymentResponse response = fixture.service.reconcile(request);

        assertTrue(response.isMatched());
        assertEquals("GT10001", response.getGatewayTradeNo());
        assertEquals(PayStatusEnumVO.SUCCESS.name(), response.getLocalPayStatus());
    }

    @Test
    void shouldQueryGatewayAndCompletePaidOrderWhenWebhookMissed() {
        Fixture fixture = fixture(TradeOrderStatusEnumVO.PAY_WAIT, PayStatusEnumVO.WAIT_PAY);
        fixture.repository.payOrder.setPayChannel("ALIPAY");
        fixture.gateway.queryPayOrderId = "P10001";
        fixture.gateway.queryGatewayTradeNo = "GT10001";
        fixture.gateway.queryAmount = new BigDecimal("2399.00");
        fixture.gateway.queryTradeStatus = "TRADE_SUCCESS";

        PaymentWebhookResponse response = fixture.service.queryGatewayAndCompleteIfPaid("O10001");

        assertTrue(response.isVerified());
        assertEquals(TradeOrderStatusEnumVO.PAY_SUCCESS, fixture.repository.tradeOrder.getOrderStatus());
        assertEquals(PayStatusEnumVO.SUCCESS, fixture.repository.payOrder.getPayStatus());
        assertEquals("GT10001", response.getGatewayTradeNo());
        assertTrue(fixture.flowRepository.flows.stream()
                .anyMatch(flow -> TradeStatusFlowService.EVENT_RECONCILE_PAYMENT.equals(flow.getEventType())));
    }

    private Fixture fixture(TradeOrderStatusEnumVO orderStatus, PayStatusEnumVO payStatus) {
        return fixture(orderStatus, payStatus, new PaymentWebhookReplayGuard(300L));
    }

    private Fixture fixture(TradeOrderStatusEnumVO orderStatus,
                            PayStatusEnumVO payStatus,
                            PaymentWebhookReplayGuard replayGuard) {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository(orderStatus, payStatus);
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        TradeStatusFlowService flowService = new TradeStatusFlowService(flowRepository);
        TradeOrderService tradeOrderService = new TradeOrderService();
        CountingPaymentCompletionService paymentCompletionService = new CountingPaymentCompletionService(
                repository,
                tradeOrderService,
                new GroupBuySettlementService(null, repository, flowService),
                flowService);
        FakePaymentGatewayClient gateway = new FakePaymentGatewayClient();
        return new Fixture(
                new PaymentService(repository, tradeOrderService, paymentCompletionService,
                        gateway, replayGuard, flowService),
                repository,
                flowRepository,
                gateway,
                paymentCompletionService);
    }

    private record Fixture(PaymentService service,
                           FakeTradeOrderRepository repository,
                           FakeTradeStatusFlowRepository flowRepository,
                           FakePaymentGatewayClient gateway,
                           CountingPaymentCompletionService paymentCompletionService) {
    }

    private static class CountingPaymentCompletionService extends PaymentCompletionService {

        private int completeCount;

        private CountingPaymentCompletionService(TradeOrderRepository tradeOrderRepository,
                                                 TradeOrderService tradeOrderService,
                                                 GroupBuySettlementService groupBuySettlementService,
                                                 TradeStatusFlowService tradeStatusFlowService) {
            super(tradeOrderRepository, tradeOrderService, groupBuySettlementService, tradeStatusFlowService);
        }

        @Override
        public PaymentCompletionResult complete(PaymentCompletionCommand command) {
            completeCount++;
            return super.complete(command);
        }
    }

    private static class FakePaymentGatewayClient implements PaymentGatewayClient {

        private PaymentCreateCommand createCommand;
        private PaymentRefundCommand refundCommand;
        private int refundCallCount;
        private String webhookPayOrderId;
        private String webhookGatewayTradeNo;
        private BigDecimal webhookAmount;
        private String webhookTradeStatus;
        private String queryPayOrderId;
        private String queryGatewayTradeNo;
        private BigDecimal queryAmount;
        private String queryTradeStatus;
        private String refundQueryStatus;
        private String refundQueryRefundId;
        private BigDecimal refundQueryAmount;
        private String refundWebhookStatus;
        private String refundWebhookRefundId;
        private BigDecimal refundWebhookAmount;

        @Override
        public PaymentCreateResult createPayment(PaymentCreateCommand command) {
            this.createCommand = command;
            return PaymentCreateResult.created(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    command.getPayChannel(),
                    "https://pay.example.com/" + command.getPayOrderId(),
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
            this.refundCommand = command;
            refundCallCount++;
            return PaymentRefundResult.success(command.getOrderId(), command.getPayOrderId(), command.getRefundId(), "refunded");
        }

        @Override
        public PaymentReconcileResult reconcile(PaymentReconcileCommand command) {
            return PaymentReconcileResult.matched(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    command.getGatewayTradeNo(),
                    "matched");
        }

        @Override
        public PaymentWebhookResult queryPayment(PaymentReconcileCommand command) {
            if (queryTradeStatus == null) {
                return null;
            }
            return PaymentWebhookResult.verified(
                    command.getOrderId(),
                    queryPayOrderId == null ? command.getPayOrderId() : queryPayOrderId,
                    queryGatewayTradeNo == null ? command.getGatewayTradeNo() : queryGatewayTradeNo,
                    LocalDateTime.of(2026, 5, 14, 10, 0),
                    "QUERY10001",
                    LocalDateTime.now(),
                    queryAmount,
                    queryTradeStatus,
                    "query paid");
        }

        @Override
        public PaymentRefundQueryResult queryRefund(PaymentRefundQueryCommand command) {
            return refundQueryResult(
                    command,
                    refundQueryStatus,
                    refundQueryRefundId,
                    refundQueryAmount,
                    "refund query");
        }

        @Override
        public PaymentRefundQueryResult verifyRefundWebhook(PaymentRefundQueryCommand command) {
            return refundQueryResult(
                    command,
                    refundWebhookStatus,
                    refundWebhookRefundId,
                    refundWebhookAmount,
                    "refund webhook");
        }

        private PaymentRefundQueryResult refundQueryResult(PaymentRefundQueryCommand command,
                                                           String refundStatus,
                                                           String refundId,
                                                           BigDecimal refundAmount,
                                                           String message) {
            return new PaymentRefundQueryResult(
                    command.payChannel(),
                    command.orderId(),
                    command.payOrderId(),
                    command.gatewayTradeNo(),
                    refundId == null ? command.refundId() : refundId,
                    refundStatus == null ? "UNKNOWN" : refundStatus,
                    refundAmount,
                    LocalDateTime.of(2026, 5, 14, 10, 0),
                    refundStatus != null,
                    "",
                    message);
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private final TradeOrderEntity tradeOrder;
        private final PayOrderEntity payOrder;
        private RefundOrderEntity refundOrder;
        private boolean failNextUpdatePaySuccess;
        private int updatePaySuccessCount;

        private FakeTradeOrderRepository(TradeOrderStatusEnumVO orderStatus, PayStatusEnumVO payStatus) {
            tradeOrder = new TradeOrderEntity();
            tradeOrder.setOrderId("O10001");
            tradeOrder.setUserId("U10001");
            tradeOrder.setGoodsId("G10001");
            tradeOrder.setGoodsName("基础 Agent 额度包");
            tradeOrder.setBuyType(TradeBuyTypeEnumVO.DIRECT);
            tradeOrder.setOriginAmount(new BigDecimal("2399.00"));
            tradeOrder.setPayAmount(new BigDecimal("2399.00"));
            tradeOrder.setOrderStatus(orderStatus);
            tradeOrder.setCreateTime(LocalDateTime.now());

            payOrder = PayOrderEntity.waitPay(
                    "P10001",
                    "O10001",
                    new BigDecimal("2399.00"),
                    "ALIPAY",
                    null,
                    LocalDateTime.now());
            payOrder.setPayStatus(payStatus);
        }

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            updatePaySuccessCount++;
            if (failNextUpdatePaySuccess) {
                failNextUpdatePaySuccess = false;
                this.tradeOrder.setOrderStatus(TradeOrderStatusEnumVO.PAY_WAIT);
                this.tradeOrder.setPayTime(null);
                this.payOrder.setPayStatus(PayStatusEnumVO.WAIT_PAY);
                this.payOrder.setOutTradeNo(null);
                this.payOrder.setPayTime(null);
                throw new AppException("TEST_0001", "db update failed");
            }
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

    private static class FakeReplayRepository implements PaymentWebhookReplayRepository {

        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean acquireProcessingLock(String replayKey, Duration ttl) {
            return keys.add(replayKey);
        }

        @Override
        public void releaseProcessingLock(String replayKey) {
            keys.remove(replayKey);
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









