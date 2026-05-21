package com.linrun.trigger.service;

import com.linrun.api.marketing.request.LockGroupBuyOrderRequest;
import com.linrun.api.marketing.response.LockGroupBuyOrderResponse;
import com.linrun.api.mall.request.CreatePayRequest;
import com.linrun.api.mall.request.NotifyRequest;
import com.linrun.api.mall.request.QueryOrderListRequest;
import com.linrun.api.mall.request.RefundOrderRequest;
import com.linrun.api.mall.response.QueryOrderListResponse;
import com.linrun.api.mall.response.RefundOrderResponse;
import com.linrun.api.payment.request.CreatePaymentRequest;
import com.linrun.api.payment.request.ReconcilePaymentRequest;
import com.linrun.api.payment.response.CreatePaymentResponse;
import com.linrun.api.payment.response.ReconcilePaymentResponse;
import com.linrun.api.order.request.CreateDirectOrderRequest;
import com.linrun.api.order.response.CreateDirectOrderResponse;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LegacyMallPayService {

    private static final int MARKET_TYPE_GROUP_BUY = 1;
    private static final String DEFAULT_PAY_CHANNEL = "MOCK_PAY";

    private final DirectBuyOrderService directBuyOrderService;
    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final PaymentService paymentService;
    private final TradeCompensationService tradeCompensationService;
    private final TradeOrderRepository tradeOrderRepository;

    public LegacyMallPayService(DirectBuyOrderService directBuyOrderService,
                                GroupBuyLockOrderService groupBuyLockOrderService,
                                PaymentService paymentService,
                                TradeCompensationService tradeCompensationService,
                                TradeOrderRepository tradeOrderRepository) {
        this.directBuyOrderService = directBuyOrderService;
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.paymentService = paymentService;
        this.tradeCompensationService = tradeCompensationService;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    public String createPayOrder(CreatePayRequest request) {
        validateCreatePayRequest(request);
        TradeBuyTypeEnumVO buyType = isGroupBuy(request) ? TradeBuyTypeEnumVO.GROUP_BUY : TradeBuyTypeEnumVO.DIRECT;
        TradeOrderEntity existed = tradeOrderRepository.queryLatestUnpaidOrder(
                        request.getUserId(), request.getProductId(), buyType)
                .orElse(null);
        if (existed != null) {
            return createGatewayPayment(existed.getOrderId(), resolvePayChannel(request)).getPayUrl();
        }
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(buyType)) {
            LockGroupBuyOrderResponse response = groupBuyLockOrderService.lock(toGroupBuyRequest(request));
            return createGatewayPayment(response.getOrderId(), resolvePayChannel(request)).getPayUrl();
        }

        CreateDirectOrderResponse response = directBuyOrderService.createDirectOrder(toDirectRequest(request));
        return createGatewayPayment(response.getOrderId(), resolvePayChannel(request)).getPayUrl();
    }

    public String groupBuyNotify(NotifyRequest request) {
        if (request == null || request.getOutTradeNoList() == null || request.getOutTradeNoList().isEmpty()) {
            throw new AppException("0001", "outTradeNoList cannot be empty");
        }
        tradeOrderRepository.updateGroupSettledByOrderIds(request.getOutTradeNoList());
        return "success";
    }

    public QueryOrderListResponse queryUserOrderList(QueryOrderListRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "userId cannot be blank");
        }
        int pageSize = request.getPageSize() == null || request.getPageSize() <= 0 ? 10 : request.getPageSize();
        List<TradeOrderEntity> orders = tradeOrderRepository.queryUserTradeOrders(
                request.getUserId(), request.getLastId(), pageSize + 1);
        boolean hasMore = orders.size() > pageSize;
        if (hasMore) {
            orders = orders.subList(0, pageSize);
        }

        QueryOrderListResponse response = new QueryOrderListResponse();
        response.setHasMore(hasMore);
        response.setOrderList(orders.stream().map(this::toOrderInfo).toList());
        response.setLastId(orders.isEmpty() ? request.getLastId() : orders.get(orders.size() - 1).getId());
        return response;
    }

    public RefundOrderResponse refundOrder(RefundOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "orderId cannot be blank");
        }
        boolean success = tradeCompensationService.refundOrCloseOrder(
                request.getUserId(), request.getOrderId(), resolveRefundReason(request));

        RefundOrderResponse response = new RefundOrderResponse();
        response.setSuccess(success);
        response.setOrderId(request.getOrderId());
        response.setMessage(success ? "success" : "order not found or user mismatch");
        return response;
    }

    public String activePayNotify(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new AppException("0001", "outTradeNo cannot be blank");
        }
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setOrderId(outTradeNo);
        ReconcilePaymentResponse response = paymentService.reconcile(request);
        return response.getMessage();
    }

    private QueryOrderListResponse.OrderInfo toOrderInfo(TradeOrderEntity order) {
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(order.getOrderId()).orElse(null);
        QueryOrderListResponse.OrderInfo info = new QueryOrderListResponse.OrderInfo();
        info.setId(order.getId());
        info.setUserId(order.getUserId());
        info.setProductId(order.getGoodsId());
        info.setProductName(order.getGoodsName());
        info.setOrderId(order.getOrderId());
        info.setOrderTime(order.getCreateTime());
        info.setTotalAmount(order.getOriginAmount());
        info.setStatus(order.getOrderStatus() == null ? null : order.getOrderStatus().name());
        info.setPayUrl(payOrder == null ? "" : payOrder.getPayUrl());
        info.setMarketType(TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType()) ? MARKET_TYPE_GROUP_BUY : 0);
        info.setPayAmount(order.getPayAmount());
        info.setMarketDeductionAmount(order.getOriginAmount() == null || order.getPayAmount() == null
                ? BigDecimal.ZERO
                : order.getOriginAmount().subtract(order.getPayAmount()));
        info.setPayTime(order.getPayTime());
        return info;
    }

    private CreatePaymentResponse createGatewayPayment(String orderId, String payChannel) {
        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setPayChannel(payChannel);
        return paymentService.createPayment(paymentRequest);
    }

    private CreateDirectOrderRequest toDirectRequest(CreatePayRequest request) {
        CreateDirectOrderRequest directRequest = new CreateDirectOrderRequest();
        directRequest.setUserId(request.getUserId());
        directRequest.setGoodsId(request.getProductId());
        directRequest.setIdempotentKey(resolveIdempotentKey(request));
        directRequest.setPayChannel(resolvePayChannel(request));
        return directRequest;
    }

    private LockGroupBuyOrderRequest toGroupBuyRequest(CreatePayRequest request) {
        LockGroupBuyOrderRequest groupRequest = new LockGroupBuyOrderRequest();
        groupRequest.setUserId(request.getUserId());
        groupRequest.setGoodsId(request.getProductId());
        groupRequest.setActivityId(request.getActivityId());
        groupRequest.setTeamId(request.getTeamId());
        groupRequest.setPayChannel(resolvePayChannel(request));
        groupRequest.setIdempotentKey(resolveIdempotentKey(request));
        return groupRequest;
    }

    private void validateCreatePayRequest(CreatePayRequest request) {
        if (request == null) {
            throw new AppException("0001", "request cannot be null");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "userId cannot be blank");
        }
        if (!StringUtils.hasText(request.getProductId())) {
            throw new AppException("0001", "productId cannot be blank");
        }
        if (isGroupBuy(request) && !StringUtils.hasText(request.getActivityId())) {
            throw new AppException("0001", "activityId cannot be blank");
        }
    }

    private boolean isGroupBuy(CreatePayRequest request) {
        return request != null && Integer.valueOf(MARKET_TYPE_GROUP_BUY).equals(request.getMarketType());
    }

    private String resolvePayChannel(CreatePayRequest request) {
        return StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : DEFAULT_PAY_CHANNEL;
    }

    private String resolveIdempotentKey(CreatePayRequest request) {
        if (StringUtils.hasText(request.getIdempotentKey())) {
            return request.getIdempotentKey();
        }
        return "PAY:" + request.getUserId() + ":" + request.getProductId() + ":"
                + request.getActivityId() + ":" + UUID.randomUUID();
    }

    private String resolveRefundReason(RefundOrderRequest request) {
        return StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : "user refund";
    }
}
