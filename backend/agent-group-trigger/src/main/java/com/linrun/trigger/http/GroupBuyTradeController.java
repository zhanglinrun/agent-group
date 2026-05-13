package com.linrun.trigger.http;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
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

    public GroupBuyTradeController(GroupBuyLockOrderService groupBuyLockOrderService) {
        this.groupBuyLockOrderService = groupBuyLockOrderService;
    }

    @PostMapping("/lock")
    public Response<LockGroupBuyOrderResponse> lock(@RequestBody LockGroupBuyOrderRequest request) {
        return Response.success(groupBuyLockOrderService.lock(request), RequestTraceContext.getRequestId());
    }
}
