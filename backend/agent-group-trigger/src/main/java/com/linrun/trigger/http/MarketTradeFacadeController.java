package com.linrun.trigger.http;

import com.linrun.api.dto.GoodsMarketRequest;
import com.linrun.api.dto.LockMarketPayOrderRequest;
import com.linrun.api.dto.RefundMarketPayOrderRequest;
import com.linrun.api.dto.SettlementMarketPayOrderRequest;
import com.linrun.api.dto.GoodsMarketResponse;
import com.linrun.api.dto.LockMarketPayOrderResponse;
import com.linrun.api.dto.RefundMarketPayOrderResponse;
import com.linrun.api.dto.SettlementMarketPayOrderResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.config.RateLimiterAccessInterceptor;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class MarketTradeFacadeController {

    private final MarketTradeFacadeHandler marketTradeFacadeService;
    private final UserAccountService userAccountService;

    public MarketTradeFacadeController(MarketTradeFacadeHandler marketTradeFacadeService,
                                       UserAccountService userAccountService) {
        this.marketTradeFacadeService = marketTradeFacadeService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/api/v1/gbm/trade/lock_market_pay_order")
    public Response<LockMarketPayOrderResponse> lockMarketPayOrder(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody LockMarketPayOrderRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        if (request != null) {
            request.setUserId(user.getUserId());
        }
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
    @RateLimiterAccessInterceptor(key = "token", fallbackMethod = "queryGroupBuyMarketConfigFallBack",
            permitsPerSecond = 1.0d, blacklistCount = 1)
    public Response<GoodsMarketResponse> queryGroupBuyMarketConfig(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody GoodsMarketRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        if (request != null) {
            request.setUserId(user.getUserId());
        }
        return Response.success(marketTradeFacadeService.queryGroupBuyMarketConfig(request), RequestTraceContext.getRequestId());
    }

    public Response<GoodsMarketResponse> queryGroupBuyMarketConfigFallBack(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody GoodsMarketRequest request) {
        return Response.fail("0006", "接口限流", RequestTraceContext.getRequestId());
    }
}
