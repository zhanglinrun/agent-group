package com.linrun.trigger.http;

import com.linrun.api.trade.request.CreateDirectOrderRequest;
import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.api.trade.response.CreateDirectOrderResponse;
import com.linrun.api.trade.response.MockPayCallbackResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.DirectBuyOrderService;
import com.linrun.trigger.service.MockPayCallbackService;
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
    private final MockPayCallbackService mockPayCallbackService;

    public TradeOrderController(DirectBuyOrderService directBuyOrderService, MockPayCallbackService mockPayCallbackService) {
        this.directBuyOrderService = directBuyOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
    }

    @PostMapping("/direct")
    public Response<CreateDirectOrderResponse> createDirectOrder(@RequestBody CreateDirectOrderRequest request) {
        return Response.success(directBuyOrderService.createDirectOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/mock-pay-success")
    public Response<MockPayCallbackResponse> mockPaySuccess(@RequestBody MockPayCallbackRequest request) {
        return Response.success(mockPayCallbackService.paySuccess(request), RequestTraceContext.getRequestId());
    }
}
