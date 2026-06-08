package com.linrun.trigger.http.trade;

import com.linrun.api.dto.CreateDirectOrderRequest;
import com.linrun.api.dto.CreateDirectOrderResponse;
import com.linrun.api.dto.QueryOrderListRequest;
import com.linrun.api.dto.QueryOrderListResponse;
import com.linrun.api.dto.QueryRefundOrderListRequest;
import com.linrun.api.dto.QueryRefundOrderListResponse;
import com.linrun.api.dto.TradeStatusFlowDTO;
import com.linrun.api.dto.TradeConsistencyCheckRequest;
import com.linrun.api.dto.TradeConsistencyCheckResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.service.DirectBuyOrderService;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
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
    private final TradeConsistencyCheckService tradeConsistencyCheckService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final UserAccountService userAccountService;
    private final TradeOrderRepository tradeOrderRepository;

    public TradeOrderController(DirectBuyOrderService directBuyOrderService,
                                TradeConsistencyCheckService tradeConsistencyCheckService,
                                TradeStatusFlowService tradeStatusFlowService,
                                UserAccountService userAccountService,
                                TradeOrderRepository tradeOrderRepository) {
        this.directBuyOrderService = directBuyOrderService;
        this.tradeConsistencyCheckService = tradeConsistencyCheckService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.userAccountService = userAccountService;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @PostMapping("/direct")
    public Response<CreateDirectOrderResponse> createDirectOrder(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody CreateDirectOrderRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        if (request != null) {
            request.setUserId(user.getUserId());
        }
        return Response.success(directBuyOrderService.createDirectOrder(request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/status-flow")
    public Response<List<TradeStatusFlowDTO>> queryStatusFlow(@RequestParam String orderId) {
        return Response.success(tradeStatusFlowService.queryByOrderId(orderId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/admin")
    public Response<QueryOrderListResponse> queryAdminOrders(@RequestBody(required = false) QueryOrderListRequest request) {
        QueryOrderListRequest safeRequest = request == null ? new QueryOrderListRequest() : request;
        int safePageSize = safePageSize(safeRequest.getPageSize() == null ? 20 : safeRequest.getPageSize(), 100);
        List<TradeOrderEntity> orders = StringUtils.hasText(safeRequest.getUserId())
                ? tradeOrderRepository.queryUserTradeOrders(
                        safeRequest.getUserId(),
                        safeRequest.getLastId(),
                        safePageSize + 1,
                        safeRequest.getMarketType(),
                        safeRequest.getOrderStatus(),
                        safeRequest.getKeyword())
                : tradeOrderRepository.queryTradeOrders(
                        safeRequest.getLastId(),
                        safePageSize + 1,
                        safeRequest.getMarketType(),
                        safeRequest.getOrderStatus(),
                        safeRequest.getKeyword());

        QueryOrderListResponse response = new QueryOrderListResponse();
        response.setHasMore(orders.size() > safePageSize);
        orders.stream().limit(safePageSize).forEach(order -> response.getOrderList().add(toOrderInfo(order)));
        response.setLastId(response.getOrderList().isEmpty()
                ? safeRequest.getLastId()
                : response.getOrderList().get(response.getOrderList().size() - 1).getId());
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    @PostMapping("/admin/refunds")
    public Response<QueryRefundOrderListResponse> queryAdminRefunds(@RequestBody(required = false) QueryRefundOrderListRequest request) {
        QueryRefundOrderListRequest safeRequest = request == null ? new QueryRefundOrderListRequest() : request;
        int safePageSize = safePageSize(safeRequest.getPageSize() == null ? 20 : safeRequest.getPageSize(), 100);
        List<RefundOrderEntity> refunds = tradeOrderRepository.queryRefundOrders(
                safeRequest.getUserId(),
                safeRequest.getRefundStatus(),
                safePageSize);

        QueryRefundOrderListResponse response = new QueryRefundOrderListResponse();
        response.setRefundList(refunds.stream().map(this::toRefundInfo).toList());
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    @PostMapping("/admin/consistency")
    public Response<TradeConsistencyCheckResponse> checkTradeConsistency(@RequestBody(required = false) TradeConsistencyCheckRequest request) {
        return Response.success(tradeConsistencyCheckService.check(request), RequestTraceContext.getRequestId());
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
        int safePageSize = safePageSize(pageSize, 50);
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
        info.setDisplayStatus(TradeDisplayStatusResolver.resolve(order, tradeOrderRepository));
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

    private int safePageSize(int pageSize, int maxPageSize) {
        return Math.max(1, Math.min(pageSize, maxPageSize));
    }
}
