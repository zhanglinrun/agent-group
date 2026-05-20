package com.linrun.trigger.http;

import com.linrun.api.marketing.request.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.marketing.request.LockGroupBuyOrderRequest;
import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.api.marketing.response.LockGroupBuyOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.GroupBuyCompensationService;
import com.linrun.trigger.service.GroupBuyLockOrderService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/group/trade")
public class GroupBuyTradeController {

    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final GroupBuyCompensationService groupBuyCompensationService;

    public GroupBuyTradeController(GroupBuyLockOrderService groupBuyLockOrderService,
                                   GroupBuyCompensationService groupBuyCompensationService) {
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.groupBuyCompensationService = groupBuyCompensationService;
    }

    @PostMapping("/lock")
    public Response<LockGroupBuyOrderResponse> lock(@RequestBody LockGroupBuyOrderRequest request) {
        return Response.success(groupBuyLockOrderService.lock(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/close-unpaid")
    public Response<GroupBuyCompensationResponse> closeUnpaid(@RequestBody CloseUnpaidGroupBuyOrderRequest request) {
        return Response.success(groupBuyCompensationService.closeUnpaid(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund")
    public Response<GroupBuyCompensationResponse> refund(@RequestBody RefundGroupBuyOrderRequest request) {
        return Response.success(groupBuyCompensationService.refundUnsettled(request), RequestTraceContext.getRequestId());
    }
}
