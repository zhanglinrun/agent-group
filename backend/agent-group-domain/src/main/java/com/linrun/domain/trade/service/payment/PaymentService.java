package com.linrun.domain.trade.service.payment;

import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.DownloadPaymentBillRequest;
import com.linrun.api.dto.DownloadPaymentBillResponse;
import com.linrun.api.dto.PaymentGatewayErrorMapResponse;
import com.linrun.api.dto.PaymentGatewayStatusResponse;
import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.QueryPaymentRefundRequest;
import com.linrun.api.dto.QueryPaymentRefundResponse;
import com.linrun.api.dto.ReconcilePaymentRequest;
import com.linrun.api.dto.RefreshPaymentCertificateRequest;
import com.linrun.api.dto.RefreshPaymentCertificateResponse;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.api.dto.PaymentWebhookResponse;
import com.linrun.api.dto.ReconcilePaymentResponse;
import com.linrun.api.dto.RefundPaymentResponse;
import com.linrun.domain.trade.adapter.port.PaymentGatewayClient;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadCommand;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadResult;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshCommand;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshResult;
import com.linrun.domain.trade.model.payment.PaymentChannel;
import com.linrun.domain.trade.model.payment.PaymentCompletionCommand;
import com.linrun.domain.trade.model.payment.PaymentCompletionResult;
import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentGatewayErrorMapping;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryResult;
import com.linrun.domain.trade.model.payment.PaymentRefundResult;
import com.linrun.domain.trade.model.payment.PaymentWebhookCommand;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.TradeOrderService;
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
    private final PaymentCompletionService paymentCompletionService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentWebhookReplayGuard paymentWebhookReplayGuard;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final AgentObservabilityMetrics metrics;

    public PaymentService(TradeOrderRepository tradeOrderRepository,
                          TradeOrderService tradeOrderService,
                          PaymentCompletionService paymentCompletionService,
                          PaymentGatewayClient paymentGatewayClient,
                          PaymentWebhookReplayGuard paymentWebhookReplayGuard,
                          TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, paymentCompletionService,
                paymentGatewayClient, paymentWebhookReplayGuard, tradeStatusFlowService,
                AgentObservabilityMetrics.noop());
    }

    @Autowired
    public PaymentService(TradeOrderRepository tradeOrderRepository,
                          TradeOrderService tradeOrderService,
                          PaymentCompletionService paymentCompletionService,
                          PaymentGatewayClient paymentGatewayClient,
                          PaymentWebhookReplayGuard paymentWebhookReplayGuard,
                          TradeStatusFlowService tradeStatusFlowService,
                          AgentObservabilityMetrics metrics) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.paymentCompletionService = paymentCompletionService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.paymentWebhookReplayGuard = paymentWebhookReplayGuard;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        return createPayment(request, null);
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request, String userId) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        validateOrderOwner(tradeOrder, userId);
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        if (!PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())) {
            return toExistingCreateResponse(payOrder, "payment already " + payOrder.getPayStatus());
        }
        String payChannel = resolvePayChannel(request.getPayChannel(), payOrder);
        PaymentCreateResult result = paymentGatewayClient.createPayment(toCreateCommand(
                tradeOrder,
                payOrder,
                payChannel,
                request.getNotifyUrl(),
                request.getReturnUrl()));
        applyGatewayPaymentResult(payOrder, result);
        tradeOrderRepository.updatePaymentGatewayInfo(payOrder);

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

    private void validateOrderOwner(TradeOrderEntity tradeOrder, String userId) {
        if (StringUtils.hasText(userId) && !userId.equals(tradeOrder.getUserId())) {
            throw new AppException("TRADE_0016", "order not found or user mismatch");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentWebhookResponse handleWebhook(PaymentWebhookRequest request) {
        long startNanos = System.nanoTime();
        if (request == null) {
            throw new AppException("0001", "支付回调参数不能为空");
        }
        PaymentChannel payChannel = PaymentChannel.parse(request.getPayChannel());
        PaymentWebhookResult webhookResult = paymentGatewayClient.verifyWebhook(toWebhookCommand(request));
        if (!webhookResult.isVerified()) {
            throw new AppException("PAY_0002", "支付回调验签失败");
        }
        if (!StringUtils.hasText(webhookResult.getOrderId())) {
            throw new AppException("0001", "支付回调缺少订单编号");
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
            PaymentCompletionResult completionResult = completePaidOrder(webhookResult);

            tradeStatusFlowService.record(
                    completionResult.getOrderId(),
                    TradeStatusFlowService.BIZ_PAY,
                    completionResult.getPayOrderId(),
                    TradeStatusFlowService.EVENT_PAYMENT_WEBHOOK_VERIFIED,
                    null,
                    completionResult.getPayStatus(),
                    webhookResult.getMessage());
            metrics.recordPaymentWebhook(payChannel.name(), "success", elapsedMillis(startNanos));
            return toWebhookResponse(webhookResult, completionResult);
        } finally {
            if (!releaseAfterCompletion) {
                paymentWebhookReplayGuard.releaseProcessingLock(payChannel, webhookResult);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refund(RefundPaymentRequest request) {
        return refund(request, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refundFromSystem(RefundPaymentRequest request) {
        return refund(request, true);
    }

    private RefundPaymentResponse refund(RefundPaymentRequest request, boolean systemRequest) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        resolvePayChannel(payOrder.getPayChannel(), payOrder);
        RefundOrderEntity existed = tradeOrderRepository.queryRefundOrderByOrderId(tradeOrder.getOrderId()).orElse(null);
        if (existed != null) {
            return toRefundResponse(tradeOrder, payOrder, existed, "退款已存在，按幂等结果返回");
        }
        validateRefundableOrder(tradeOrder, payOrder);

        String refundId = nextRefundId(tradeOrder.getOrderId());
        PaymentRefundResult gatewayResult = paymentGatewayClient.refund(
                toRefundCommand(tradeOrder, payOrder, request, systemRequest, refundId));
        LocalDateTime refundTime = LocalDateTime.now();
        RefundOrderEntity refundOrder = RefundOrderEntity.success(
                StringUtils.hasText(gatewayResult.getRefundId()) ? gatewayResult.getRefundId() : refundId,
                tradeOrder,
                payOrder,
                resolveRefundReason(request, systemRequest),
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
        PaymentChannel.parse(payOrder.getPayChannel());
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

    public DownloadPaymentBillResponse downloadBill(DownloadPaymentBillRequest request) {
        if (request == null || !StringUtils.hasText(request.getPayChannel())) {
            throw new AppException("0001", "支付渠道不能为空");
        }
        PaymentBillDownloadResult result = paymentGatewayClient.downloadBill(new PaymentBillDownloadCommand(
                PaymentChannel.parse(request.getPayChannel()).name(),
                request.getBillDate() == null ? LocalDate.now() : request.getBillDate(),
                StringUtils.hasText(request.getBillType()) ? request.getBillType() : "trade",
                request.isDownloadContent(),
                request.getBillFileUrl()));
        return toBillDownloadResponse(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public QueryPaymentRefundResponse queryRefund(QueryPaymentRefundRequest request) {
        PaymentRefundQueryCommand command = toRefundQueryCommand(request);
        PaymentRefundQueryResult result = paymentGatewayClient.queryRefund(command);
        applyRefundResultIfSuccess(result);
        return toRefundQueryResponse(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public QueryPaymentRefundResponse handleRefundWebhook(PaymentWebhookRequest request) {
        if (request == null || !StringUtils.hasText(request.getPayChannel())) {
            throw new AppException("0001", "支付渠道不能为空");
        }
        PaymentRefundQueryCommand command = new PaymentRefundQueryCommand(
                PaymentChannel.parse(request.getPayChannel()).name(),
                request.getOrderId(),
                request.getPayOrderId(),
                request.getGatewayTradeNo(),
                null,
                request.getRequestBody(),
                request.getHeaders());
        PaymentRefundQueryResult result = paymentGatewayClient.verifyRefundWebhook(command);
        applyRefundResultIfSuccess(result);
        return toRefundQueryResponse(result);
    }

    public RefreshPaymentCertificateResponse refreshCertificate(RefreshPaymentCertificateRequest request) {
        if (request == null || !StringUtils.hasText(request.getPayChannel())) {
            throw new AppException("0001", "支付渠道不能为空");
        }
        PaymentCertificateRefreshResult result = paymentGatewayClient.refreshCertificate(
                new PaymentCertificateRefreshCommand(PaymentChannel.parse(request.getPayChannel()).name()));
        return toCertificateRefreshResponse(result);
    }

    public PaymentGatewayErrorMapResponse mapGatewayError(String payChannel, String gatewayCode) {
        if (!StringUtils.hasText(payChannel) || !StringUtils.hasText(gatewayCode)) {
            throw new AppException("0001", "支付渠道和渠道错误码不能为空");
        }
        PaymentGatewayErrorMapping mapping = paymentGatewayClient.mapGatewayError(
                PaymentChannel.parse(payChannel).name(), gatewayCode);
        PaymentGatewayErrorMapResponse response = new PaymentGatewayErrorMapResponse();
        response.setPayChannel(mapping.payChannel());
        response.setGatewayCode(mapping.gatewayCode());
        response.setBusinessCode(mapping.businessCode());
        response.setBusinessMessage(mapping.businessMessage());
        response.setRetryable(mapping.retryable());
        response.setSuggestion(mapping.suggestion());
        return response;
    }

    public PaymentGatewayStatusResponse gatewayStatus() {
        return paymentGatewayClient.gatewayStatus();
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentWebhookResponse queryGatewayAndCompleteIfPaid(String orderId) {
        long startNanos = System.nanoTime();
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(orderId);
        PayOrderEntity payOrder = queryPayOrder(orderId);
        PaymentChannel payChannel = PaymentChannel.parse(payOrder.getPayChannel());
        if (PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            PaymentWebhookResult result = PaymentWebhookResult.verified(
                    tradeOrder.getOrderId(),
                    payOrder.getPayOrderId(),
                    payOrder.getOutTradeNo(),
                    payOrder.getPayTime(),
                    payOrder.getPayAmount(),
                    successTradeStatus(payChannel),
                    "local payment already success");
            return queryExistingWebhookResponse(result);
        }
        if (!PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())) {
            return null;
        }

        PaymentWebhookResult queryResult = paymentGatewayClient.queryPayment(
                toReconcileCommand(tradeOrder, payOrder, LocalDate.now()));
        if (queryResult == null || !queryResult.isVerified()) {
            recordPaymentQueryFlow(tradeOrder, payOrder,
                    queryResult == null ? "gateway payment query no result" : queryResult.getMessage());
            return null;
        }
        normalizeQueryResult(queryResult, tradeOrder, payOrder);
        if (!isSuccessTradeStatus(payChannel, queryResult.getTradeStatus())) {
            recordPaymentQueryFlow(tradeOrder, payOrder, queryResult.getMessage());
            return null;
        }
        validateWebhookConsistency(payChannel, queryResult);

        PaymentWebhookResponse existingResponse = queryExistingWebhookResponse(queryResult);
        if (existingResponse != null) {
            metrics.recordPaymentWebhook(payChannel.name(), "query_duplicate_completed", elapsedMillis(startNanos));
            return existingResponse;
        }
        if (!paymentWebhookReplayGuard.acquireProcessingLock(payChannel, queryResult)) {
            existingResponse = queryExistingWebhookResponse(queryResult);
            if (existingResponse != null) {
                metrics.recordPaymentWebhook(payChannel.name(), "query_duplicate_completed", elapsedMillis(startNanos));
                return existingResponse;
            }
            metrics.recordPaymentWebhook(payChannel.name(), "query_processing_conflict", elapsedMillis(startNanos));
            return null;
        }

        boolean releaseAfterCompletion = registerWebhookProcessingLockRelease(payChannel, queryResult);
        try {
            PaymentCompletionResult completionResult = completePaidOrder(queryResult);
            tradeStatusFlowService.record(
                    completionResult.getOrderId(),
                    TradeStatusFlowService.BIZ_PAY,
                    completionResult.getPayOrderId(),
                    TradeStatusFlowService.EVENT_RECONCILE_PAYMENT,
                    null,
                    completionResult.getPayStatus(),
                    queryResult.getMessage());
            metrics.recordPaymentWebhook(payChannel.name(), "query_success", elapsedMillis(startNanos));
            return toWebhookResponse(queryResult, completionResult);
        } finally {
            if (!releaseAfterCompletion) {
                paymentWebhookReplayGuard.releaseProcessingLock(payChannel, queryResult);
            }
        }
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

    private void applyGatewayPaymentResult(PayOrderEntity payOrder, PaymentCreateResult result) {
        if (result == null) {
            throw new AppException("PAY_0003", "payment gateway create result is empty");
        }
        payOrder.markGatewayCreated(
                result.getPayChannel(),
                resolveGatewayPayUrl(result),
                result.getGatewayTradeNo());
    }

    private String resolveGatewayPayUrl(PaymentCreateResult result) {
        if (StringUtils.hasText(result.getPayFormHtml())) {
            return result.getPayFormHtml();
        }
        return result.getPayUrl();
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
        if (!StringUtils.hasText(webhookResult.getPayOrderId())) {
            throw new AppException("PAY_0010", "payment webhook missing pay order id");
        }
        if (StringUtils.hasText(webhookResult.getPayOrderId())
                && !webhookResult.getPayOrderId().equals(payOrder.getPayOrderId())) {
            throw new AppException("PAY_0010", "payment webhook pay order mismatch");
        }
        if (!StringUtils.hasText(webhookResult.getGatewayTradeNo())) {
            throw new AppException("PAY_0013", "payment webhook missing gateway trade no");
        }
        if (PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())
                && StringUtils.hasText(payOrder.getOutTradeNo())
                && StringUtils.hasText(webhookResult.getGatewayTradeNo())
                && !payOrder.getOutTradeNo().equals(webhookResult.getGatewayTradeNo())) {
            throw new AppException("PAY_0015", "payment webhook gateway trade no mismatch");
        }
        if (webhookResult.getPayAmount() != null
                && normalize(webhookResult.getPayAmount()).compareTo(normalize(payOrder.getPayAmount())) != 0) {
            throw new AppException("PAY_0012", "payment webhook amount mismatch");
        }
        if (StringUtils.hasText(webhookResult.getTradeStatus())
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
        return toWebhookResponse(webhookResult, completePaidOrder(webhookResult));
    }

    private PaymentCompletionResult completePaidOrder(PaymentWebhookResult webhookResult) {
        return paymentCompletionService.complete(PaymentCompletionCommand.paid(
                webhookResult.getOrderId(),
                resolveGatewayTradeNo(webhookResult),
                webhookResult.getPayTime() == null ? LocalDateTime.now() : webhookResult.getPayTime()));
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

    private void validateRefundableOrder(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        TradeOrderStatusEnumVO orderStatus = tradeOrder.getOrderStatus();
        if (!TradeOrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus)
                && !TradeOrderStatusEnumVO.GROUP_SETTLED.equals(orderStatus)
                && !TradeOrderStatusEnumVO.DEAL_DONE.equals(orderStatus)) {
            throw new AppException("TRADE_0015", "当前订单状态不能退款");
        }
        if (!PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            throw new AppException("TRADE_0016", "当前支付单状态不能退款");
        }
        if (!StringUtils.hasText(payOrder.getOutTradeNo())) {
            throw new AppException("PAY_0018", "退款缺少网关交易号");
        }
    }

    private String nextRefundId(String orderId) {
        String normalized = orderId == null ? "" : orderId.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(normalized)) {
            return "R" + normalized;
        }
        return "R" + LocalDateTime.now().format(ORDER_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void applyRefundResultIfSuccess(PaymentRefundQueryResult result) {
        if (result == null) {
            throw new AppException("PAY_0018", "退款查询结果为空");
        }
        if (!result.verified() || !isRefundSuccessStatus(result.refundStatus())) {
            return;
        }
        if (!StringUtils.hasText(result.orderId())) {
            throw new AppException("PAY_0020", "退款结果缺少订单编号");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(result.orderId());
        PayOrderEntity payOrder = queryPayOrder(result.orderId());
        validateRefundResultConsistency(result, tradeOrder, payOrder);
        RefundOrderEntity existed = tradeOrderRepository.queryRefundOrderByOrderId(tradeOrder.getOrderId()).orElse(null);
        if (isLocalRefunded(tradeOrder, payOrder)) {
            return;
        }
        if (existed == null) {
            validateRefundableOrder(tradeOrder, payOrder);
            existed = RefundOrderEntity.success(
                    StringUtils.hasText(result.refundId()) ? result.refundId() : nextRefundId(tradeOrder.getOrderId()),
                    tradeOrder,
                    payOrder,
                    "网关退款结果确认",
                    result.refundTime() == null ? LocalDateTime.now() : result.refundTime());
            tradeOrderService.refundPaidOrder(tradeOrder, payOrder);
            tradeOrderRepository.saveRefundOrder(existed);
            tradeOrderRepository.updateRefunded(tradeOrder, payOrder);
            recordRefundFlow(tradeOrder, payOrder, existed);
            return;
        }
        validateRefundableOrder(tradeOrder, payOrder);
        tradeOrderService.refundPaidOrder(tradeOrder, payOrder);
        tradeOrderRepository.updateRefunded(tradeOrder, payOrder);
        recordRefundFlow(tradeOrder, payOrder, existed);
    }

    private void validateRefundResultConsistency(PaymentRefundQueryResult result,
                                                 TradeOrderEntity tradeOrder,
                                                 PayOrderEntity payOrder) {
        if (StringUtils.hasText(result.payChannel())
                && !PaymentChannel.parse(payOrder.getPayChannel()).equals(PaymentChannel.parse(result.payChannel()))) {
            throw new AppException("PAY_0020", "退款结果支付渠道不匹配");
        }
        if (StringUtils.hasText(result.payOrderId()) && !result.payOrderId().equals(payOrder.getPayOrderId())) {
            throw new AppException("PAY_0020", "退款结果支付单号不匹配");
        }
        if (StringUtils.hasText(result.gatewayTradeNo())
                && StringUtils.hasText(payOrder.getOutTradeNo())
                && !result.gatewayTradeNo().equals(payOrder.getOutTradeNo())) {
            throw new AppException("PAY_0020", "退款结果网关交易号不匹配");
        }
        if (result.refundAmount() != null
                && result.refundAmount().compareTo(BigDecimal.ZERO) > 0
                && normalize(result.refundAmount()).compareTo(normalize(payOrder.getPayAmount())) != 0) {
            throw new AppException("PAY_0020", "退款金额和支付金额不匹配");
        }
    }

    private boolean isLocalRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        return TradeOrderStatusEnumVO.REFUNDED.equals(tradeOrder.getOrderStatus())
                && PayStatusEnumVO.REFUNDED.equals(payOrder.getPayStatus());
    }

    private boolean isRefundSuccessStatus(String refundStatus) {
        if (!StringUtils.hasText(refundStatus)) {
            return false;
        }
        String normalized = refundStatus.trim().toUpperCase(Locale.ROOT);
        return "SUCCESS".equals(normalized)
                || "REFUND_SUCCESS".equals(normalized)
                || "REFUNDED".equals(normalized);
    }

    private PaymentRefundCommand toRefundCommand(TradeOrderEntity tradeOrder, PayOrderEntity payOrder,
                                                 RefundPaymentRequest request, boolean systemRequest,
                                                 String refundId) {
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setOrderId(tradeOrder.getOrderId());
        command.setPayOrderId(payOrder.getPayOrderId());
        command.setRefundId(refundId);
        command.setPayChannel(payOrder.getPayChannel());
        command.setGatewayTradeNo(payOrder.getOutTradeNo());
        command.setRefundAmount(payOrder.getPayAmount());
        command.setRefundReason(resolveRefundReason(request, systemRequest));
        return command;
    }

    private PaymentReconcileCommand toReconcileCommand(TradeOrderEntity tradeOrder,
                                                       PayOrderEntity payOrder,
                                                       ReconcilePaymentRequest request) {
        return toReconcileCommand(tradeOrder, payOrder,
                request.getBillDate() == null ? LocalDate.now() : request.getBillDate());
    }

    private PaymentReconcileCommand toReconcileCommand(TradeOrderEntity tradeOrder,
                                                       PayOrderEntity payOrder,
                                                       LocalDate billDate) {
        PaymentReconcileCommand command = new PaymentReconcileCommand();
        command.setOrderId(tradeOrder.getOrderId());
        command.setPayOrderId(payOrder.getPayOrderId());
        command.setPayChannel(payOrder.getPayChannel());
        command.setGatewayTradeNo(payOrder.getOutTradeNo());
        command.setBillDate(billDate == null ? LocalDate.now() : billDate);
        return command;
    }

    private PaymentRefundQueryCommand toRefundQueryCommand(QueryPaymentRefundRequest request) {
        if (request == null) {
            throw new AppException("0001", "退款查询参数不能为空");
        }
        if (StringUtils.hasText(request.getOrderId())) {
            TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
            PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
            RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(request.getOrderId()).orElse(null);
            return new PaymentRefundQueryCommand(
                    payOrder.getPayChannel(),
                    tradeOrder.getOrderId(),
                    payOrder.getPayOrderId(),
                    StringUtils.hasText(request.getGatewayTradeNo()) ? request.getGatewayTradeNo() : payOrder.getOutTradeNo(),
                    StringUtils.hasText(request.getRefundId()) ? request.getRefundId() : refundOrder == null ? null : refundOrder.getRefundId(),
                    null,
                    null);
        }
        if (!StringUtils.hasText(request.getPayChannel()) || !StringUtils.hasText(request.getPayOrderId())) {
            throw new AppException("0001", "退款查询缺少支付渠道或支付单号");
        }
        return new PaymentRefundQueryCommand(
                PaymentChannel.parse(request.getPayChannel()).name(),
                request.getOrderId(),
                request.getPayOrderId(),
                request.getGatewayTradeNo(),
                request.getRefundId(),
                null,
                null);
    }

    private CreatePaymentResponse toCreateResponse(PaymentCreateResult result, PayOrderEntity payOrder) {
        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setOrderId(result.getOrderId());
        response.setPayOrderId(result.getPayOrderId());
        response.setPayChannel(result.getPayChannel());
        response.setPayUrl(result.getPayUrl());
        response.setPayFormHtml(result.getPayFormHtml());
        response.setPaymentType(result.getPaymentType());
        response.setGatewayTradeNo(result.getGatewayTradeNo());
        response.setPayAmount(payOrder.getPayAmount());
        response.setCreated(result.isCreated());
        response.setMessage(result.getMessage());
        return response;
    }

    private CreatePaymentResponse toExistingCreateResponse(PayOrderEntity payOrder, String message) {
        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setOrderId(payOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setPayChannel(payOrder.getPayChannel());
        response.setPayUrl(payOrder.getPayUrl());
        if (looksLikePaymentForm(payOrder.getPayUrl())) {
            response.setPayFormHtml(payOrder.getPayUrl());
            response.setPaymentType("PAGE_FORM");
        } else {
            response.setPaymentType("URL");
        }
        response.setGatewayTradeNo(payOrder.getOutTradeNo());
        response.setPayAmount(payOrder.getPayAmount());
        response.setCreated(false);
        response.setMessage(message);
        return response;
    }

    private boolean looksLikePaymentForm(String value) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains("<form");
    }

    private DownloadPaymentBillResponse toBillDownloadResponse(PaymentBillDownloadResult result) {
        DownloadPaymentBillResponse response = new DownloadPaymentBillResponse();
        response.setPayChannel(result.payChannel());
        response.setBillDate(result.billDate());
        response.setBillType(result.billType());
        response.setDownloadUrl(result.downloadUrl());
        response.setDownloaded(result.downloaded());
        response.setParsed(result.parsed());
        response.setTotalCount(result.totalCount());
        response.setTotalAmount(result.totalAmount());
        response.setSummary(result.summary());
        response.setMessage(result.message());
        return response;
    }

    private QueryPaymentRefundResponse toRefundQueryResponse(PaymentRefundQueryResult result) {
        QueryPaymentRefundResponse response = new QueryPaymentRefundResponse();
        response.setPayChannel(result.payChannel());
        response.setOrderId(result.orderId());
        response.setPayOrderId(result.payOrderId());
        response.setGatewayTradeNo(result.gatewayTradeNo());
        response.setRefundId(result.refundId());
        response.setRefundStatus(result.refundStatus());
        response.setRefundAmount(result.refundAmount());
        response.setRefundTime(result.refundTime());
        response.setVerified(result.verified());
        response.setRawBody(result.rawBody());
        response.setMessage(result.message());
        return response;
    }

    private RefreshPaymentCertificateResponse toCertificateRefreshResponse(PaymentCertificateRefreshResult result) {
        RefreshPaymentCertificateResponse response = new RefreshPaymentCertificateResponse();
        response.setPayChannel(result.payChannel());
        response.setRefreshed(result.refreshed());
        response.setCertificateSerialNo(result.certificateSerialNo());
        response.setRefreshTime(result.refreshTime());
        response.setMessage(result.message());
        return response;
    }

    private PaymentWebhookResponse toWebhookResponse(PaymentWebhookResult result,
                                                     PaymentCompletionResult completionResult) {
        PaymentWebhookResponse response = new PaymentWebhookResponse();
        response.setOrderId(completionResult.getOrderId());
        response.setPayOrderId(completionResult.getPayOrderId());
        response.setOrderStatus(completionResult.getOrderStatus());
        response.setPayStatus(completionResult.getPayStatus());
        response.setGatewayTradeNo(completionResult.getGatewayTradeNo());
        response.setPayTime(completionResult.getPayTime());
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
        response.setRefundStatus(refundOrder.getRefundStatus() == null ? null : refundOrder.getRefundStatus().name());
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

    private void recordPaymentQueryFlow(TradeOrderEntity tradeOrder, PayOrderEntity payOrder, String message) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_RECONCILE_PAYMENT,
                null,
                payOrder.getPayStatus(),
                StringUtils.hasText(message) ? message : "gateway payment query not paid");
    }

    private void normalizeQueryResult(PaymentWebhookResult result,
                                      TradeOrderEntity tradeOrder,
                                      PayOrderEntity payOrder) {
        if (!StringUtils.hasText(result.getOrderId())) {
            result.setOrderId(tradeOrder.getOrderId());
        }
        if (!StringUtils.hasText(result.getPayOrderId())) {
            result.setPayOrderId(payOrder.getPayOrderId());
        }
        if (result.getPayAmount() == null) {
            result.setPayAmount(payOrder.getPayAmount());
        }
        if (result.getPayTime() == null) {
            result.setPayTime(LocalDateTime.now());
        }
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
                : payOrder == null ? defaultPayChannel() : payOrder.getPayChannel();
        return PaymentChannel.parse(candidate).name();
    }

    private String defaultPayChannel() {
        return PaymentChannel.ALIPAY.name();
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
        if (!StringUtils.hasText(tradeStatus)) {
            return false;
        }
        String normalized = tradeStatus.trim().toUpperCase(Locale.ROOT);
        return switch (payChannel) {
            case ALIPAY -> "TRADE_SUCCESS".equals(normalized) || "TRADE_FINISHED".equals(normalized);
            case WECHAT_PAY -> "SUCCESS".equals(normalized);
        };
    }

    private String successTradeStatus(PaymentChannel payChannel) {
        return switch (payChannel) {
            case ALIPAY -> "TRADE_SUCCESS";
            case WECHAT_PAY -> "SUCCESS";
        };
    }

    /**
     * 退款原因兜底：系统发起（超时补偿等定时任务）和用户发起使用不同的默认文案，
     * 落到退款单和流水里便于审计区分。
     */
    private String resolveRefundReason(RefundPaymentRequest request, boolean systemRequest) {
        if (StringUtils.hasText(request.getRefundReason())) {
            return systemRequest ? "[系统] " + request.getRefundReason() : request.getRefundReason();
        }
        return systemRequest ? "系统自动退款" : "用户申请退款";
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}











