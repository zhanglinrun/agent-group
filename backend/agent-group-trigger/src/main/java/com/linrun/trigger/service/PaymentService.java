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
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PaymentService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final MockPayCallbackService mockPayCallbackService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final TradeStatusFlowService tradeStatusFlowService;

    public PaymentService(TradeOrderRepository tradeOrderRepository,
                          TradeOrderService tradeOrderService,
                          MockPayCallbackService mockPayCallbackService,
                          PaymentGatewayClient paymentGatewayClient,
                          TradeStatusFlowService tradeStatusFlowService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        String payChannel = resolvePayChannel(request.getPayChannel(), payOrder);
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
        return toWebhookResponse(webhookResult, callbackResponse);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundPaymentResponse refund(RefundPaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
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
        command.setPayTime(request.getPayTime());
        return command;
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

    private String resolveRefundReason(RefundPaymentRequest request) {
        return StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : "用户申请退款";
    }
}
