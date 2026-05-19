package com.linrun.trigger.http;

import com.linrun.api.market.request.GoodsMarketRequest;
import com.linrun.api.market.request.LockMarketPayOrderRequest;
import com.linrun.api.market.request.RefundMarketPayOrderRequest;
import com.linrun.api.market.request.SettlementMarketPayOrderRequest;
import com.linrun.api.market.response.GoodsMarketResponse;
import com.linrun.api.market.response.LockMarketPayOrderResponse;
import com.linrun.api.market.response.RefundMarketPayOrderResponse;
import com.linrun.api.market.response.SettlementMarketPayOrderResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.MarketTradeFacadeService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class MarketTradeFacadeController {

    private final MarketTradeFacadeService marketTradeFacadeService;

    public MarketTradeFacadeController(MarketTradeFacadeService marketTradeFacadeService) {
        this.marketTradeFacadeService = marketTradeFacadeService;
    }

    @PostMapping("/api/v1/gbm/trade/lock_market_pay_order")
    public Response<LockMarketPayOrderResponse> lockMarketPayOrder(@RequestBody LockMarketPayOrderRequest request) {
        return Response.success(marketTradeFacadeService.lockMarketPayOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/api/v1/gbm/trade/settlement_market_pay_order")
    public Response<SettlementMarketPayOrderResponse> settlementMarketPayOrder(@RequestBody SettlementMarketPayOrderRequest request) {
        return Response.success(marketTradeFacadeService.settlementMarketPayOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/api/v1/gbm/trade/refund_market_pay_order")
    public Response<RefundMarketPayOrderResponse> refundMarketPayOrder(@RequestBody RefundMarketPayOrderRequest request) {
        return Response.success(marketTradeFacadeService.refundMarketPayOrder(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/api/v1/gbm/index/query_group_buy_market_config")
    public Response<GoodsMarketResponse> queryGroupBuyMarketConfig(@RequestBody GoodsMarketRequest request) {
        return Response.success(marketTradeFacadeService.queryGroupBuyMarketConfig(request), RequestTraceContext.getRequestId());
    }
}
