package com.linrun.trigger.http;

import com.linrun.api.dto.CreateDirectOrderRequest;
import com.linrun.api.dto.MockPayCallbackRequest;
import com.linrun.api.dto.CreateDirectOrderResponse;
import com.linrun.api.dto.MockPayCallbackResponse;
import com.linrun.api.dto.QueryOrderListResponse;
import com.linrun.api.dto.TradeStatusFlowDTO;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.DirectBuyOrderService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.domain.trade.service.payment.MockPayCallbackService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/trade/order")
public class TradeOrderController {

    private final DirectBuyOrderService directBuyOrderService;
    private final MockPayCallbackService mockPayCallbackService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final UserAccountService userAccountService;
    private final TradeOrderRepository tradeOrderRepository;

    public TradeOrderController(DirectBuyOrderService directBuyOrderService,
                                MockPayCallbackService mockPayCallbackService,
                                TradeStatusFlowService tradeStatusFlowService,
                                UserAccountService userAccountService,
                                TradeOrderRepository tradeOrderRepository) {
        this.directBuyOrderService = directBuyOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.userAccountService = userAccountService;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @PostMapping("/direct")
    public Response<CreateDirectOrderResponse> createDirectOrder(@RequestBody CreateDirectOrderRequest request) {
        return Response.success(directBuyOrderService.createDirectOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/mock-pay-success")
    public Response<MockPayCallbackResponse> mockPaySuccess(@RequestBody MockPayCallbackRequest request) {
        return Response.success(mockPayCallbackService.paySuccess(request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/status-flow")
    public Response<List<TradeStatusFlowDTO>> queryStatusFlow(@RequestParam String orderId) {
        return Response.success(tradeStatusFlowService.queryByOrderId(orderId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/my")
    public Response<QueryOrderListResponse> queryMyOrders(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer marketType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String keyword) {
        UserAccount user = userAccountService.requireUserByToken(token);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        List<TradeOrderEntity> orders = tradeOrderRepository.queryUserTradeOrders(
                user.getUserId(),
                lastId,
                safePageSize + 1,
                marketType,
                orderStatus,
                keyword);
        QueryOrderListResponse response = new QueryOrderListResponse();
        response.setHasMore(orders.size() > safePageSize);
        orders.stream().limit(safePageSize).forEach(order -> response.getOrderList().add(toOrderInfo(order)));
        response.setLastId(response.getOrderList().isEmpty()
                ? lastId
                : response.getOrderList().get(response.getOrderList().size() - 1).getId());
        return Response.success(response, RequestTraceContext.getRequestId());
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
        info.setStatus(order.getOrderStatus() == null ? "" : order.getOrderStatus().name());
        info.setDisplayStatus(resolveDisplayStatus(order));
        info.setPayAmount(order.getPayAmount());
        info.setPayTime(order.getPayTime());
        info.setPayUrl(payOrder == null ? "" : payOrder.getPayUrl());
        info.setMarketType(TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType()) ? 1 : 0);
        if (order.getOriginAmount() != null && order.getPayAmount() != null) {
            info.setMarketDeductionAmount(order.getOriginAmount().subtract(order.getPayAmount()));
        }
        if (StringUtils.hasText(info.getPayUrl()) && payOrder != null && payOrder.getPayStatus() != null) {
            info.setPayUrl(payOrder.getPayUrl());
        }
        return info;
    }

    private String resolveDisplayStatus(TradeOrderEntity order) {
        if (order == null || order.getOrderStatus() == null) {
            return "-";
        }
        boolean groupOrder = TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType());
        if (groupOrder && TradeOrderStatusEnumVO.CLOSED.equals(order.getOrderStatus())) {
            return "拼团失败（已关闭）";
        }
        if (groupOrder && TradeOrderStatusEnumVO.REFUNDED.equals(order.getOrderStatus())) {
            RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(order.getOrderId()).orElse(null);
            if (refundOrder != null && isGroupTimeoutRefund(refundOrder.getRefundReason())) {
                return "拼团失败（已退款）";
            }
            return "已退款";
        }
        if (groupOrder && TradeOrderStatusEnumVO.PAY_SUCCESS.equals(order.getOrderStatus())) {
            return "等待成团";
        }
        return switch (order.getOrderStatus()) {
            case CREATE -> "已创建";
            case PAY_WAIT -> "待支付";
            case PAY_SUCCESS -> "已支付";
            case GROUP_SETTLED -> "已成团";
            case DEAL_DONE -> "已到账";
            case CLOSED -> "已关闭";
            case WAIT_REFUND -> "待退款";
            case REFUNDED -> "已退款";
        };
    }

    private boolean isGroupTimeoutRefund(String refundReason) {
        if (!StringUtils.hasText(refundReason)) {
            return false;
        }
        String reason = refundReason.trim().toLowerCase();
        return reason.contains("group buy timeout")
                || reason.contains("timeout unformed")
                || refundReason.contains("拼团超时")
                || refundReason.contains("未成团");
    }
}
