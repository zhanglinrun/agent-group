package com.linrun.trigger.http;

import com.linrun.api.trade.request.CreateDirectOrderRequest;
import com.linrun.api.trade.response.CreateDirectOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.DirectBuyOrderService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/trade/order")
public class TradeOrderController {

    private final DirectBuyOrderService directBuyOrderService;

    public TradeOrderController(DirectBuyOrderService directBuyOrderService) {
        this.directBuyOrderService = directBuyOrderService;
    }

    @PostMapping("/direct")
    public Response<CreateDirectOrderResponse> createDirectOrder(@RequestBody CreateDirectOrderRequest request) {
        return Response.success(directBuyOrderService.createDirectOrder(request), RequestTraceContext.getRequestId());
    }
}
