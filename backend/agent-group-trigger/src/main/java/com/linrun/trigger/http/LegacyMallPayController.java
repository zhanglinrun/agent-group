package com.linrun.trigger.http;

import com.linrun.api.mall.request.CreatePayRequest;
import com.linrun.api.mall.request.NotifyRequest;
import com.linrun.api.mall.request.QueryOrderListRequest;
import com.linrun.api.mall.request.RefundOrderRequest;
import com.linrun.api.mall.response.QueryOrderListResponse;
import com.linrun.api.mall.response.RefundOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.LegacyMallPayService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/alipay")
public class LegacyMallPayController {

    private final LegacyMallPayService legacyMallPayService;

    public LegacyMallPayController(LegacyMallPayService legacyMallPayService) {
        this.legacyMallPayService = legacyMallPayService;
    }

    @PostMapping("/create_pay_order")
    public Response<String> createPayOrder(@RequestBody CreatePayRequest request) {
        return Response.success(legacyMallPayService.createPayOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/group_buy_notify")
    public String groupBuyNotify(@RequestBody NotifyRequest request) {
        return legacyMallPayService.groupBuyNotify(request);
    }

    @PostMapping("/query_user_order_list")
    public Response<QueryOrderListResponse> queryUserOrderList(@RequestBody QueryOrderListRequest request) {
        return Response.success(legacyMallPayService.queryUserOrderList(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund_order")
    public Response<RefundOrderResponse> refundOrder(@RequestBody RefundOrderRequest request) {
        return Response.success(legacyMallPayService.refundOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/active_pay_notify")
    public Response<String> activePayNotify(@RequestParam String outTradeNo) {
        return Response.success(legacyMallPayService.activePayNotify(outTradeNo), RequestTraceContext.getRequestId());
    }
}
