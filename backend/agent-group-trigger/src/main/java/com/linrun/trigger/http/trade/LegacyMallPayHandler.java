package com.linrun.trigger.http.trade;





import com.linrun.domain.trade.service.TradeCompensationService;
import com.linrun.domain.groupbuy.service.GroupBuyLockOrderService;
import com.linrun.domain.trade.service.DirectBuyOrderService;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.api.dto.LockGroupBuyOrderRequest;
import com.linrun.api.dto.LockGroupBuyOrderResponse;
import com.linrun.api.dto.CreatePayRequest;
import com.linrun.api.dto.NotifyRequest;
import com.linrun.api.dto.QueryOrderListRequest;
import com.linrun.api.dto.QueryRefundOrderListRequest;
import com.linrun.api.dto.RefundOrderRequest;
import com.linrun.api.dto.QueryOrderListResponse;
import com.linrun.api.dto.QueryRefundOrderListResponse;
import com.linrun.api.dto.RefundOrderResponse;
import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.ReconcilePaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.api.dto.ReconcilePaymentResponse;
import com.linrun.api.dto.CreateDirectOrderRequest;
import com.linrun.api.dto.CreateDirectOrderResponse;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LegacyMallPayHandler {

    private static final int MARKET_TYPE_GROUP_BUY = 1;
    private static final String DEFAULT_PAY_CHANNEL = "ALIPAY";

    private final DirectBuyOrderService directBuyOrderService;
    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final PaymentService paymentService;
    private final TradeCompensationService tradeCompensationService;
    private final TradeOrderRepository tradeOrderRepository;

    public LegacyMallPayHandler(DirectBuyOrderService directBuyOrderService,
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
            throw new AppException("0001", "成团通知订单列表不能为空");
        }
        tradeOrderRepository.updateGroupSettledByOrderIds(request.getOutTradeNoList());
        return "success";
    }

    public QueryOrderListResponse queryUserOrderList(QueryOrderListRequest request) {
        QueryOrderListRequest safeRequest = request == null ? new QueryOrderListRequest() : request;
        int pageSize = safeRequest.getPageSize() == null || safeRequest.getPageSize() <= 0
                ? 10
                : Math.min(safeRequest.getPageSize(), 100);
        List<TradeOrderEntity> orders = StringUtils.hasText(safeRequest.getUserId())
                ? tradeOrderRepository.queryUserTradeOrders(
                        safeRequest.getUserId(),
                        safeRequest.getLastId(),
                        pageSize + 1,
                        safeRequest.getMarketType(),
                        safeRequest.getOrderStatus(),
                        safeRequest.getKeyword())
                : tradeOrderRepository.queryTradeOrders(
                        safeRequest.getLastId(),
                        pageSize + 1,
                        safeRequest.getMarketType(),
                        safeRequest.getOrderStatus(),
                        safeRequest.getKeyword());
        boolean hasMore = orders.size() > pageSize;
        if (hasMore) {
            orders = orders.subList(0, pageSize);
        }

        QueryOrderListResponse response = new QueryOrderListResponse();
        response.setHasMore(hasMore);
        response.setOrderList(orders.stream().map(this::toOrderInfo).toList());
        response.setLastId(orders.isEmpty() ? safeRequest.getLastId() : orders.get(orders.size() - 1).getId());
        return response;
    }

    public QueryRefundOrderListResponse queryRefundOrderList(QueryRefundOrderListRequest request) {
        int pageSize = request == null || request.getPageSize() == null || request.getPageSize() <= 0
                ? 20
                : Math.min(request.getPageSize(), 100);
        List<RefundOrderEntity> refunds = tradeOrderRepository.queryRefundOrders(
                request == null ? null : request.getUserId(),
                request == null ? null : request.getRefundStatus(),
                pageSize);
        QueryRefundOrderListResponse response = new QueryRefundOrderListResponse();
        response.setRefundList(refunds.stream().map(this::toRefundInfo).toList());
        return response;
    }

    public RefundOrderResponse refundOrder(RefundOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        boolean success = tradeCompensationService.refundOrCloseOrder(
                request.getUserId(), request.getOrderId(), resolveRefundReason(request));

        RefundOrderResponse response = new RefundOrderResponse();
        response.setSuccess(success);
        response.setOrderId(request.getOrderId());
        response.setMessage(success ? "退款处理成功" : "订单状态暂不支持退款");
        return response;
    }

    public String activePayNotify(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new AppException("0001", "外部交易单号不能为空");
        }
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setOrderId(outTradeNo);
        ReconcilePaymentResponse response = paymentService.reconcile(request);
        return response.getMessage();
    }

    public String alipayNotify(String requestBody, Map<String, String> params) {
        try {
            PaymentWebhookRequest request = new PaymentWebhookRequest();
            request.setPayChannel("ALIPAY");
            request.setRequestBody(StringUtils.hasText(requestBody) ? requestBody : formBody(params));
            paymentService.handleWebhook(request);
            return "success";
        } catch (Exception e) {
            return "false";
        }
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
        info.setDisplayStatus(TradeDisplayStatusResolver.resolve(order, tradeOrderRepository));
        info.setPayUrl(payOrder == null ? "" : payOrder.getPayUrl());
        info.setMarketType(TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType()) ? MARKET_TYPE_GROUP_BUY : 0);
        info.setPayAmount(order.getPayAmount());
        info.setMarketDeductionAmount(order.getOriginAmount() == null || order.getPayAmount() == null
                ? BigDecimal.ZERO
                : order.getOriginAmount().subtract(order.getPayAmount()));
        info.setPayTime(order.getPayTime());
        return info;
    }

    private QueryRefundOrderListResponse.RefundInfo toRefundInfo(RefundOrderEntity refundOrder) {
        QueryRefundOrderListResponse.RefundInfo info = new QueryRefundOrderListResponse.RefundInfo();
        info.setId(refundOrder.getId());
        info.setRefundId(refundOrder.getRefundId());
        info.setOrderId(refundOrder.getOrderId());
        info.setPayOrderId(refundOrder.getPayOrderId());
        info.setUserId(refundOrder.getUserId());
        info.setRefundAmount(refundOrder.getRefundAmount());
        info.setRefundStatus(refundOrder.getRefundStatus() == null ? null : refundOrder.getRefundStatus().name());
        info.setRefundReason(refundOrder.getRefundReason());
        info.setCreateTime(refundOrder.getCreateTime());
        info.setRefundTime(refundOrder.getRefundTime());
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
        directRequest.setDecisionId(request.getDecisionId());
        directRequest.setIdempotentKey(resolveIdempotentKey(request));
        directRequest.setPayChannel(resolvePayChannel(request));
        return directRequest;
    }

    private LockGroupBuyOrderRequest toGroupBuyRequest(CreatePayRequest request) {
        LockGroupBuyOrderRequest groupRequest = new LockGroupBuyOrderRequest();
        groupRequest.setUserId(request.getUserId());
        groupRequest.setGoodsId(request.getProductId());
        groupRequest.setDecisionId(request.getDecisionId());
        groupRequest.setActivityId(request.getActivityId());
        groupRequest.setTeamId(request.getTeamId());
        groupRequest.setPayChannel(resolvePayChannel(request));
        groupRequest.setIdempotentKey(resolveIdempotentKey(request));
        return groupRequest;
    }

    private void validateCreatePayRequest(CreatePayRequest request) {
        if (request == null) {
            throw new AppException("0001", "请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(request.getProductId())) {
            throw new AppException("0001", "商品编号不能为空");
        }
        if (isGroupBuy(request) && !StringUtils.hasText(request.getActivityId())) {
            throw new AppException("0001", "拼团活动编号不能为空");
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
        return StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : "用户发起退款";
    }

    private String formBody(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}















