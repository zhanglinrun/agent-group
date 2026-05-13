package com.linrun.trigger.http;

import com.linrun.api.trade.request.CreateDirectOrderRequest;
import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.api.trade.response.CreateDirectOrderResponse;
import com.linrun.api.trade.response.MockPayCallbackResponse;
import com.linrun.api.trade.response.TradeStatusFlowDTO;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.DirectBuyOrderService;
import com.linrun.trigger.service.MockPayCallbackService;
import com.linrun.trigger.service.TradeStatusFlowService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/trade/order")
public class TradeOrderController {

    private final DirectBuyOrderService directBuyOrderService;
    private final MockPayCallbackService mockPayCallbackService;
    private final TradeStatusFlowService tradeStatusFlowService;

    public TradeOrderController(DirectBuyOrderService directBuyOrderService,
                                MockPayCallbackService mockPayCallbackService,
                                TradeStatusFlowService tradeStatusFlowService) {
        this.directBuyOrderService = directBuyOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.tradeStatusFlowService = tradeStatusFlowService;
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
}
