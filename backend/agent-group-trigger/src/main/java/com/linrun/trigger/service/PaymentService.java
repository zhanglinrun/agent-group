package com.linrun.trigger.service;

import com.linrun.api.payment.request.CreatePaymentRequest;
import com.linrun.api.payment.request.PaymentWebhookRequest;
import com.linrun.api.payment.request.ReconcilePaymentRequest;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.api.payment.response.CreatePaymentResponse;
import com.linrun.api.payment.response.PaymentWebhookResponse;
import com.linrun.api.payment.response.ReconcilePaymentResponse;
import com.linrun.api.payment.response.RefundPaymentResponse;
import com.linrun.api.order.request.MockPayCallbackRequest;
import com.linrun.api.order.response.MockPayCallbackResponse;
import com.linrun.domain.payment.adapter.PaymentGatewayClient;
import com.linrun.domain.payment.model.PaymentChannel;
import com.linrun.domain.payment.model.PaymentCreateCommand;
import com.linrun.domain.payment.model.PaymentCreateResult;
import com.linrun.domain.payment.model.PaymentReconcileCommand;
import com.linrun.domain.payment.model.PaymentReconcileResult;
import com.linrun.domain.payment.model.PaymentRefundCommand;
import com.linrun.domain.payment.model.PaymentRefundResult;
import com.linrun.domain.payment.model.PaymentWebhookCommand;
import com.linrun.domain.payment.model.PaymentWebhookResult;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.trigger.config.MockPaymentAccessChecker;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final MockPayCallbackService mockPayCallbackService;
    private final MockPaymentAccessChecker mockPaymentAccessChecker;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentWebhookReplayGuard paymentWebhookReplayGuard;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final AgentObservabilityMetrics metrics;

    public PaymentService(TradeOrderRepository tradeOrderRepository,
                          TradeOrderService tradeOrderService,
                          MockPayCallbackService mockPayCallbackService,
                          MockPaymentAccessChecker mockPaymentAccessChecker,
                          PaymentGatewayClient paymentGatewayClient,
                          PaymentWebhookReplayGuard paymentWebhookReplayGuard,
                          TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, mockPayCallbackService, mockPaymentAccessChecker,
                paymentGatewayClient, paymentWebhookReplayGuard, tradeStatusFlowService,
                AgentObservabilityMetrics.noop());
    }

    @Autowired
    public PaymentService(TradeOrderRepository tradeOrderRepository,
                          TradeOrderService tradeOrderService,
                          MockPayCallbackService mockPayCallbackService,
                          MockPaymentAccessChecker mockPaymentAccessChecker,
                          PaymentGatewayClient paymentGatewayClient,
                          PaymentWebhookReplayGuard paymentWebhookReplayGuard,
                          TradeStatusFlowService tradeStatusFlowService,
                          AgentObservabilityMetrics metrics) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.mockPaymentAccessChecker = mockPaymentAccessChecker;
        this.paymentGatewayClient = paymentGatewayClient;
        this.paymentWebhookReplayGuard = paymentWebhookReplayGuard;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        String payChannel = resolvePayChannel(request.getPayChannel(), payOrder);
        if (PaymentChannel.MOCK_PAY.name().equals(payChannel)) {
            mockPaymentAccessChecker.assertAllowed();
        }
        PaymentCreateResult result = paymentGatewayClient.createPayment(toCreateCommand(
                tradeOrder,
                payOrder,
                payChannel,
                request.getNotifyUrl(),
                request.getReturnUrl()));

        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_CREATE_GATEWAY_PAYMENT,
                null,
                payOrder.getPayStatus(),
                "gateway payment created by " + payChannel);
        return toCreateResponse(result, payOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentWebhookResponse handleWebhook(PaymentWebhookRequest request) {
        long startNanos = System.nanoTime();
        if (request == null) {
            throw new AppException("0001", "支付回调参数不能为空");
        }
        PaymentWebhookResult webhookResult = paymentGatewayClient.verifyWebhook(toWebhookCommand(request));
        if (!webhookResult.isVerified()) {
            throw new AppException("PAY_0002", "支付回调验签失败");
        }
        if (!StringUtils.hasText(webhookResult.getOrderId())) {
            throw new AppException("0001", "支付回调缺少订单编号");
        }
        PaymentChannel payChannel = PaymentChannel.parse(request.getPayChannel());
        if (PaymentChannel.MOCK_PAY.equals(payChannel)) {
            mockPaymentAccessChecker.assertAllowed();
        }
        validateWebhookConsistency(payChannel, webhookResult);

        PaymentWebhookResponse existingResponse = queryExistingWebhookResponse(webhookResult);
        if (existingResponse != null) {
            metrics.recordPaymentWebhook(payChannel.name(), "duplicate_completed", elapsedMillis(startNanos));
            return existingResponse;
        }
        if (!paymentWebhookReplayGuard.acquireProcessingLock(payChannel, webhookResult)) {
            existingResponse = queryExistingWebhookResponse(webhookResult);
            if (existingResponse != null) {
                metrics.recordPaymentWebhook(payChannel.name(), "duplicate_completed", elapsedMillis(startNanos));
                return existingResponse;
            }
            metrics.recordPaymentWebhook(payChannel.name(), "processing_conflict", elapsedMillis(startNanos));
            throw new AppException("PAY_0016", "支付回调正在处理中，请稍后重试");
        }

        boolean releaseAfterCompletion = registerWebhookProcessingLockRelease(payChannel, webhookResult);
        try {
            MockPayCallbackRequest callbackRequest = new MockPayCallbackRequest();
            callbackRequest.setOrderId(webhookResult.getOrderId());
            callbackRequest.setOutTradeNo(resolveGatewayTradeNo(webhookResult));
            callbackRequest.setPayTime(webhookResult.getPayTime() == null ? LocalDateTime.now() : webhookResult.getPayTime());
            MockPayCallbackResponse callbackResponse = mockPayCallbackService.paySuccess(callbackRequest);

            tradeStatusFlowService.record(
                    callbackResponse.getOrderId(),
                    TradeStatusFlowService.BIZ_PAY,
                    callbackResponse.getPayOrderId(),
                    TradeStatusFlowService.EVENT_PAYMENT_WEBHOOK_VERIFIED,
                    null,
                    callbackResponse.getPayStatus(),
                    webhookResult.getMessage());
            metrics.recordPaymentWebhook(payChannel.name(), "success", elapsedMillis(startNanos));
            return toWebhookResponse(webhookResult, callbackResponse);
        } finally {
            if (!releaseAfterCompletion) {
                paymentWebhookReplayGuard.releaseProcessingLock(payChannel, webhookResult);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refund(RefundPaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        if (PaymentChannel.MOCK_PAY.name().equals(resolvePayChannel(payOrder.getPayChannel(), payOrder))) {
            mockPaymentAccessChecker.assertAllowed();
        }
        RefundOrderEntity existed = tradeOrderRepository.queryRefundOrderByOrderId(tradeOrder.getOrderId()).orElse(null);
        if (existed != null) {
            return toRefundResponse(tradeOrder, payOrder, existed, "退款已存在，按幂等结果返回");
        }

        PaymentRefundResult gatewayResult = paymentGatewayClient.refund(toRefundCommand(tradeOrder, payOrder, request));
        LocalDateTime refundTime = LocalDateTime.now();
        RefundOrderEntity refundOrder = RefundOrderEntity.success(
                gatewayResult.getRefundId(),
                tradeOrder,
                payOrder,
                resolveRefundReason(request),
                refundTime);
        tradeOrderService.refundPaidOrder(tradeOrder, payOrder);
        tradeOrderRepository.saveRefundOrder(refundOrder);
        tradeOrderRepository.updateRefunded(tradeOrder, payOrder);
        recordRefundFlow(tradeOrder, payOrder, refundOrder);
        return toRefundResponse(tradeOrder, payOrder, refundOrder, gatewayResult.getMessage());
    }

    public ReconcilePaymentResponse reconcile(ReconcilePaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        PaymentReconcileResult result = paymentGatewayClient.reconcile(toReconcileCommand(tradeOrder, payOrder, request));
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_RECONCILE_PAYMENT,
                null,
                payOrder.getPayStatus(),
                result.getMessage());
        return toReconcileResponse(tradeOrder, payOrder, request, result);
    }

    private PaymentCreateCommand toCreateCommand(TradeOrderEntity tradeOrder,
                                                 PayOrderEntity payOrder,
                                                 String payChannel,
                                                 String notifyUrl,
                                                 String returnUrl) {
        PaymentCreateCommand command = new PaymentCreateCommand();
        command.setOrderId(tradeOrder.getOrderId());
        command.setPayOrderId(payOrder.getPayOrderId());
        command.setPayChannel(payChannel);
        command.setSubject(tradeOrder.getGoodsName());
        command.setPayAmount(payOrder.getPayAmount());
        command.setNotifyUrl(notifyUrl);
        command.setReturnUrl(returnUrl);
        return command;
    }

    private PaymentWebhookCommand toWebhookCommand(PaymentWebhookRequest request) {
        PaymentWebhookCommand command = new PaymentWebhookCommand();
        command.setPayChannel(resolvePayChannel(request.getPayChannel(), null));
        command.setRequestBody(request.getRequestBody());
        command.setHeaders(request.getHeaders());
        command.setOrderId(request.getOrderId());
        command.setPayOrderId(request.getPayOrderId());
        command.setGatewayTradeNo(request.getGatewayTradeNo());
        command.setPayAmount(request.getPayAmount());
        command.setTradeStatus(request.getTradeStatus());
        command.setPayTime(request.getPayTime());
        return command;
    }

    private void validateWebhookConsistency(PaymentChannel payChannel, PaymentWebhookResult webhookResult) {
        TradeOrderEntity tradeOrder = queryTradeOrder(webhookResult.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(webhookResult.getOrderId());
        if (!tradeOrder.getOrderId().equals(payOrder.getOrderId())) {
            throw new AppException("PAY_0010", "payment webhook local order mismatch");
        }
        if (!PaymentChannel.parse(payOrder.getPayChannel()).equals(payChannel)) {
            throw new AppException("PAY_0011", "payment webhook channel mismatch");
        }
        if (!PaymentChannel.MOCK_PAY.equals(payChannel)
                && !StringUtils.hasText(webhookResult.getPayOrderId())) {
            throw new AppException("PAY_0010", "payment webhook missing pay order id");
        }
        if (StringUtils.hasText(webhookResult.getPayOrderId())
                && !webhookResult.getPayOrderId().equals(payOrder.getPayOrderId())) {
            throw new AppException("PAY_0010", "payment webhook pay order mismatch");
        }
        if (!PaymentChannel.MOCK_PAY.equals(payChannel)
                && !StringUtils.hasText(webhookResult.getGatewayTradeNo())) {
            throw new AppException("PAY_0013", "payment webhook missing gateway trade no");
        }
        if (webhookResult.getPayAmount() != null
                && normalize(webhookResult.getPayAmount()).compareTo(normalize(payOrder.getPayAmount())) != 0) {
            throw new AppException("PAY_0012", "payment webhook amount mismatch");
        }
        if (!PaymentChannel.MOCK_PAY.equals(payChannel)
                && StringUtils.hasText(webhookResult.getTradeStatus())
                && !isSuccessTradeStatus(payChannel, webhookResult.getTradeStatus())) {
            throw new AppException("PAY_0014", "payment webhook trade status is not success");
        }
    }

    private PaymentWebhookResponse queryExistingWebhookResponse(PaymentWebhookResult webhookResult) {
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(webhookResult.getOrderId()).orElse(null);
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(webhookResult.getOrderId()).orElse(null);
        if (tradeOrder == null || payOrder == null) {
            return null;
        }
        if (!PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            return null;
        }
        return toWebhookResponse(webhookResult, existingCallbackResponse(tradeOrder, payOrder));
    }

    private MockPayCallbackResponse existingCallbackResponse(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        MockPayCallbackResponse response = new MockPayCallbackResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setOutTradeNo(payOrder.getOutTradeNo());
        response.setPayTime(payOrder.getPayTime());
        return response;
    }

    private boolean registerWebhookProcessingLockRelease(PaymentChannel payChannel, PaymentWebhookResult webhookResult) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                paymentWebhookReplayGuard.releaseProcessingLock(payChannel, webhookResult);
            }
        });
        return true;
    }

    private PaymentRefundCommand toRefundCommand(TradeOrderEntity tradeOrder, PayOrderEntity payOrder,
                                                 RefundPaymentRequest request) {
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setOrderId(tradeOrder.getOrderId());
        command.setPayOrderId(payOrder.getPayOrderId());
        command.setPayChannel(payOrder.getPayChannel());
        command.setGatewayTradeNo(payOrder.getOutTradeNo());
        command.setRefundAmount(payOrder.getPayAmount());
        command.setRefundReason(resolveRefundReason(request));
        return command;
    }

    private PaymentReconcileCommand toReconcileCommand(TradeOrderEntity tradeOrder,
                                                       PayOrderEntity payOrder,
                                                       ReconcilePaymentRequest request) {
        PaymentReconcileCommand command = new PaymentReconcileCommand();
        command.setOrderId(tradeOrder.getOrderId());
        command.setPayOrderId(payOrder.getPayOrderId());
        command.setPayChannel(payOrder.getPayChannel());
        command.setGatewayTradeNo(payOrder.getOutTradeNo());
        command.setBillDate(request.getBillDate() == null ? LocalDate.now() : request.getBillDate());
        return command;
    }

    private CreatePaymentResponse toCreateResponse(PaymentCreateResult result, PayOrderEntity payOrder) {
        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setOrderId(result.getOrderId());
        response.setPayOrderId(result.getPayOrderId());
        response.setPayChannel(result.getPayChannel());
        response.setPayUrl(result.getPayUrl());
        response.setGatewayTradeNo(result.getGatewayTradeNo());
        response.setPayAmount(payOrder.getPayAmount());
        response.setCreated(result.isCreated());
        response.setMessage(result.getMessage());
        return response;
    }

    private PaymentWebhookResponse toWebhookResponse(PaymentWebhookResult result,
                                                     MockPayCallbackResponse callbackResponse) {
        PaymentWebhookResponse response = new PaymentWebhookResponse();
        response.setOrderId(callbackResponse.getOrderId());
        response.setPayOrderId(callbackResponse.getPayOrderId());
        response.setOrderStatus(callbackResponse.getOrderStatus());
        response.setPayStatus(callbackResponse.getPayStatus());
        response.setGatewayTradeNo(callbackResponse.getOutTradeNo());
        response.setPayTime(callbackResponse.getPayTime());
        response.setVerified(result.isVerified());
        response.setMessage(result.getMessage());
        return response;
    }

    private RefundPaymentResponse toRefundResponse(TradeOrderEntity tradeOrder,
                                                   PayOrderEntity payOrder,
                                                   RefundOrderEntity refundOrder,
                                                   String message) {
        RefundPaymentResponse response = new RefundPaymentResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setRefundId(refundOrder.getRefundId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setRefundAmount(refundOrder.getRefundAmount());
        response.setRefundTime(refundOrder.getRefundTime());
        response.setMessage(message);
        return response;
    }

    private ReconcilePaymentResponse toReconcileResponse(TradeOrderEntity tradeOrder,
                                                         PayOrderEntity payOrder,
                                                         ReconcilePaymentRequest request,
                                                         PaymentReconcileResult result) {
        ReconcilePaymentResponse response = new ReconcilePaymentResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setPayChannel(payOrder.getPayChannel());
        response.setLocalOrderStatus(tradeOrder.getOrderStatus().name());
        response.setLocalPayStatus(payOrder.getPayStatus().name());
        response.setGatewayTradeNo(result.getGatewayTradeNo());
        response.setLocalPayAmount(payOrder.getPayAmount());
        response.setBillDate(request.getBillDate() == null ? LocalDate.now() : request.getBillDate());
        response.setMatched(result.isMatched());
        response.setMessage(result.getMessage());
        return response;
    }

    private void recordRefundFlow(TradeOrderEntity tradeOrder, PayOrderEntity payOrder, RefundOrderEntity refundOrder) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_REFUNDED,
                null,
                tradeOrder.getOrderStatus(),
                "gateway refund success");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_REFUNDED,
                null,
                payOrder.getPayStatus(),
                "gateway refund success");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_REFUND,
                refundOrder.getRefundId(),
                TradeStatusFlowService.EVENT_REFUND_SUCCESS,
                null,
                refundOrder.getRefundStatus(),
                "refund success");
    }

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }

    private PayOrderEntity queryPayOrder(String orderId) {
        return tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
    }

    private String resolvePayChannel(String requestChannel, PayOrderEntity payOrder) {
        String candidate = StringUtils.hasText(requestChannel)
                ? requestChannel
                : payOrder == null ? PaymentChannel.MOCK_PAY.name() : payOrder.getPayChannel();
        return PaymentChannel.parse(candidate).name();
    }

    private String resolveGatewayTradeNo(PaymentWebhookResult result) {
        if (StringUtils.hasText(result.getGatewayTradeNo())) {
            return result.getGatewayTradeNo();
        }
        if (StringUtils.hasText(result.getPayOrderId())) {
            return result.getPayOrderId();
        }
        return "GT" + LocalDateTime.now().format(ORDER_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isSuccessTradeStatus(PaymentChannel payChannel, String tradeStatus) {
        String normalized = tradeStatus.trim().toUpperCase(Locale.ROOT);
        return switch (payChannel) {
            case ALIPAY -> "TRADE_SUCCESS".equals(normalized) || "TRADE_FINISHED".equals(normalized);
            case WECHAT_PAY -> "SUCCESS".equals(normalized);
            case MOCK_PAY -> true;
        };
    }

    private String resolveRefundReason(RefundPaymentRequest request) {
        return StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : "用户申请退款";
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
