package com.linrun.trigger.http;

import com.linrun.api.dto.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.dto.LockGroupBuyOrderRequest;
import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.LockGroupBuyOrderResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.domain.trade.service.GroupBuyCompensationService;
import com.linrun.domain.trade.service.GroupBuyLockOrderService;
import com.linrun.domain.trade.service.TradeRefundService;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/group/trade")
public class GroupBuyTradeController {

    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final TradeRefundService tradeRefundService;
    private final UserAccountService userAccountService;

    public GroupBuyTradeController(GroupBuyLockOrderService groupBuyLockOrderService,
                                   GroupBuyCompensationService groupBuyCompensationService,
                                   TradeRefundService tradeRefundService,
                                   UserAccountService userAccountService) {
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.tradeRefundService = tradeRefundService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/lock")
    public Response<LockGroupBuyOrderResponse> lock(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody LockGroupBuyOrderRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        if (request != null) {
            request.setUserId(user.getUserId());
        }
        return Response.success(groupBuyLockOrderService.lock(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/close-unpaid")
    public Response<GroupBuyCompensationResponse> closeUnpaid(@RequestBody CloseUnpaidGroupBuyOrderRequest request) {
        return Response.success(groupBuyCompensationService.closeUnpaid(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund")
    public Response<GroupBuyCompensationResponse> refund(@RequestBody RefundGroupBuyOrderRequest request) {
        return Response.success(tradeRefundService.refundGroupBuy(request), RequestTraceContext.getRequestId());
    }
}
