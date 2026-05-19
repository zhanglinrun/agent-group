package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.api.market.request.GoodsMarketRequest;
import com.linrun.api.market.request.LockMarketPayOrderRequest;
import com.linrun.api.market.request.RefundMarketPayOrderRequest;
import com.linrun.api.market.request.SettlementMarketPayOrderRequest;
import com.linrun.api.market.response.GoodsMarketResponse;
import com.linrun.api.market.response.LockMarketPayOrderResponse;
import com.linrun.api.market.response.RefundMarketPayOrderResponse;
import com.linrun.api.market.response.SettlementMarketPayOrderResponse;
import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class MarketTradeFacadeService {

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final MockPayCallbackService mockPayCallbackService;
    private final TradeCompensationService tradeCompensationService;
    private final TradeOrderRepository tradeOrderRepository;
    private final DynamicConfigService dynamicConfigService;

    public MarketTradeFacadeService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyLockOrderService groupBuyLockOrderService,
                                    MockPayCallbackService mockPayCallbackService,
                                    TradeCompensationService tradeCompensationService,
                                    TradeOrderRepository tradeOrderRepository,
                                    DynamicConfigService dynamicConfigService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.tradeCompensationService = tradeCompensationService;
        this.tradeOrderRepository = tradeOrderRepository;
        this.dynamicConfigService = dynamicConfigService;
    }

    public LockMarketPayOrderResponse lockMarketPayOrder(LockMarketPayOrderRequest request) {
        validateLockRequest(request);
        LockGroupBuyOrderResponse lockResponse = groupBuyLockOrderService.lock(toGroupBuyRequest(request));
        GuideProduct product = queryProduct(request.getGoodsId());

        LockMarketPayOrderResponse response = new LockMarketPayOrderResponse();
        response.setOrderId(lockResponse.getOrderId());
        response.setOriginalPrice(product.getOriginPrice());
        response.setPayPrice(lockResponse.getLockAmount());
        response.setDeductionPrice(product.getOriginPrice().subtract(lockResponse.getLockAmount()));
        response.setTradeOrderStatus(0);
        response.setTeamId(lockResponse.getTeamId());
        return response;
    }

    public SettlementMarketPayOrderResponse settlementMarketPayOrder(SettlementMarketPayOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "outTradeNo cannot be blank");
        }
        String orderId = resolveOrderId(request.getOutTradeNo());
        TradeOrder tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "order not found"));

        MockPayCallbackRequest callbackRequest = new MockPayCallbackRequest();
        callbackRequest.setOrderId(orderId);
        callbackRequest.setOutTradeNo("SETTLE-" + request.getOutTradeNo());
        callbackRequest.setPayTime(request.getOutTradeTime() == null ? LocalDateTime.now() : request.getOutTradeTime());
        mockPayCallbackService.paySuccess(callbackRequest);

        GroupBuyOrderLock orderLock = groupBuyOrderLockRepository.queryLockByOrderId(orderId)
                .orElseThrow(() -> new AppException("GROUP_0011", "group lock not found"));

        SettlementMarketPayOrderResponse response = new SettlementMarketPayOrderResponse();
        response.setUserId(tradeOrder.getUserId());
        response.setTeamId(orderLock.getTeamId());
        response.setActivityId(orderLock.getActivityId());
        response.setOutTradeNo(request.getOutTradeNo());
        return response;
    }

    public RefundMarketPayOrderResponse refundMarketPayOrder(RefundMarketPayOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "outTradeNo cannot be blank");
        }
        String orderId = resolveOrderId(request.getOutTradeNo());
        boolean success = tradeCompensationService.refundOrCloseOrder(
                request.getUserId(), orderId, "group buy refund");
        GroupBuyOrderLock orderLock = groupBuyOrderLockRepository.queryLockByOrderId(orderId).orElse(null);

        RefundMarketPayOrderResponse response = new RefundMarketPayOrderResponse();
        response.setUserId(request.getUserId());
        response.setOrderId(orderId);
        response.setTeamId(orderLock == null ? null : orderLock.getTeamId());
        response.setCode(success ? "0000" : "0002");
        response.setInfo(success ? "success" : "refund failed");
        return response;
    }

    public GoodsMarketResponse queryGroupBuyMarketConfig(GoodsMarketRequest request) {
        if (request == null || !StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
        GuideProduct product = queryProduct(request.getGoodsId());
        GroupBuyActivity activity = groupBuyActivityRepository.queryByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("GROUP_0001", "group activity not found"));

        GoodsMarketResponse response = new GoodsMarketResponse();
        response.setActivityId(activity.getActivityId());
        GoodsMarketResponse.Goods goods = new GoodsMarketResponse.Goods();
        goods.setGoodsId(product.getGoodsId());
        goods.setOriginalPrice(product.getOriginPrice());
        goods.setPayPrice(activity.getGroupPrice());
        goods.setDeductionPrice(product.getOriginPrice().subtract(activity.getGroupPrice()));
        response.setGoods(goods);
        return response;
    }

    private LockGroupBuyOrderRequest toGroupBuyRequest(LockMarketPayOrderRequest request) {
        LockGroupBuyOrderRequest groupRequest = new LockGroupBuyOrderRequest();
        groupRequest.setUserId(request.getUserId());
        groupRequest.setGoodsId(request.getGoodsId());
        groupRequest.setActivityId(request.getActivityId());
        groupRequest.setTeamId(request.getTeamId());
        groupRequest.setIdempotentKey(resolveIdempotentKey(request));
        groupRequest.setPayChannel("MOCK_PAY");
        return groupRequest;
    }

    private void validateLockRequest(LockMarketPayOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "request cannot be null");
        }
        if (!StringUtils.hasText(request.getUserId())
                || !StringUtils.hasText(request.getGoodsId())
                || !StringUtils.hasText(request.getActivityId())
                || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "userId, goodsId, activityId and outTradeNo cannot be blank");
        }
        if (dynamicConfigService.isDowngradeSwitch()) {
            throw new AppException("DCC_0003", "group buy market is downgraded");
        }
        if (!dynamicConfigService.isCutRange(request.getUserId())) {
            throw new AppException("DCC_0004", "user is outside market cut range");
        }
        if (dynamicConfigService.isSourceChannelBlackIntercept(request.getSource(), request.getChannel())) {
            throw new AppException("DCC_0005", "source and channel are blocked");
        }
    }

    private String resolveIdempotentKey(LockMarketPayOrderRequest request) {
        return StringUtils.hasText(request.getOutTradeNo())
                ? request.getOutTradeNo()
                : request.getUserId() + ":" + request.getGoodsId() + ":" + request.getActivityId();
    }

    private GuideProduct queryProduct(String goodsId) {
        GuideProduct product = guideDataRepository.queryProductByGoodsId(goodsId)
                .orElseThrow(() -> new AppException("DATA_0003", "product not found"));
        if (product.getOriginPrice() == null) {
            product.setOriginPrice(BigDecimal.ZERO);
        }
        return product;
    }

    private String resolveOrderId(String outTradeNo) {
        if (tradeOrderRepository.queryTradeOrderByOrderId(outTradeNo).isPresent()) {
            return outTradeNo;
        }
        return groupBuyOrderLockRepository.queryLockByIdempotentKey(outTradeNo)
                .map(GroupBuyOrderLock::getOrderId)
                .orElse(outTradeNo);
    }
}
