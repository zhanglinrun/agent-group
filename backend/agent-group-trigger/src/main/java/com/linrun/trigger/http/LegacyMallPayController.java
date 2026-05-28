package com.linrun.trigger.http;

import com.linrun.api.dto.CreatePayRequest;
import com.linrun.api.dto.NotifyRequest;
import com.linrun.api.dto.QueryOrderListRequest;
import com.linrun.api.dto.QueryRefundOrderListRequest;
import com.linrun.api.dto.RefundOrderRequest;
import com.linrun.api.dto.QueryOrderListResponse;
import com.linrun.api.dto.QueryRefundOrderListResponse;
import com.linrun.api.dto.RefundOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.http.LegacyMallPayHandler;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/alipay")
public class LegacyMallPayController {

    private final LegacyMallPayHandler legacyMallPayService;

    public LegacyMallPayController(LegacyMallPayHandler legacyMallPayService) {
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

    @PostMapping("/query_refund_order_list")
    public Response<QueryRefundOrderListResponse> queryRefundOrderList(@RequestBody(required = false) QueryRefundOrderListRequest request) {
        return Response.success(legacyMallPayService.queryRefundOrderList(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund_order")
    public Response<RefundOrderResponse> refundOrder(@RequestBody RefundOrderRequest request) {
        return Response.success(legacyMallPayService.refundOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/active_pay_notify")
    public Response<String> activePayNotify(@RequestParam String outTradeNo) {
        return Response.success(legacyMallPayService.activePayNotify(outTradeNo), RequestTraceContext.getRequestId());
    }

    @PostMapping(value = "/alipay_notify_url", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String alipayNotifyUrl(@RequestBody(required = false) String requestBody,
                                  @RequestParam(required = false) java.util.Map<String, String> params) {
        return legacyMallPayService.alipayNotify(requestBody, params);
    }
}
