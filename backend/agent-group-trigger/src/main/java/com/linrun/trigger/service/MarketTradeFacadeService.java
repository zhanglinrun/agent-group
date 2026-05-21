package com.linrun.trigger.service;

import com.linrun.api.marketing.request.LockGroupBuyOrderRequest;
import com.linrun.api.marketing.response.LockGroupBuyOrderResponse;
import com.linrun.api.market.request.GoodsMarketRequest;
import com.linrun.api.market.request.LockMarketPayOrderRequest;
import com.linrun.api.market.request.RefundMarketPayOrderRequest;
import com.linrun.api.market.request.SettlementMarketPayOrderRequest;
import com.linrun.api.market.response.GoodsMarketResponse;
import com.linrun.api.market.response.LockMarketPayOrderResponse;
import com.linrun.api.market.response.RefundMarketPayOrderResponse;
import com.linrun.api.market.response.SettlementMarketPayOrderResponse;
import com.linrun.api.order.request.MockPayCallbackRequest;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyOrderLock;
import com.linrun.domain.marketing.model.GroupBuyTeamDetail;
import com.linrun.domain.marketing.model.GroupBuyTeamStatistic;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.GroupBuyMarketTrialService;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
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
    private final GroupBuyMarketTrialService groupBuyMarketTrialService;

    public MarketTradeFacadeService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyLockOrderService groupBuyLockOrderService,
                                    MockPayCallbackService mockPayCallbackService,
                                    TradeCompensationService tradeCompensationService,
                                    TradeOrderRepository tradeOrderRepository,
                                    DynamicConfigService dynamicConfigService,
                                    GroupBuyMarketTrialService groupBuyMarketTrialService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.mockPayCallbackService = mockPayCallbackService;
        this.tradeCompensationService = tradeCompensationService;
        this.tradeOrderRepository = tradeOrderRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.groupBuyMarketTrialService = groupBuyMarketTrialService;
    }

    public LockMarketPayOrderResponse lockMarketPayOrder(LockMarketPayOrderRequest request) {
        validateLockRequest(request);
        GroupBuyTrialResult trialResult = groupBuyMarketTrialService.trial(toTrialCommand(request));
        if (!request.getActivityId().equals(trialResult.getActivityId())) {
            throw new AppException("GROUP_0018", "request activity does not match market trial activity");
        }
        if (!trialResult.isEnable() || !trialResult.isAvailable()) {
            throw new AppException("GROUP_0019", "user cannot join this group activity");
        }
        LockGroupBuyOrderResponse lockResponse = groupBuyLockOrderService.lock(toGroupBuyRequest(request, trialResult));

        LockMarketPayOrderResponse response = new LockMarketPayOrderResponse();
        response.setOrderId(lockResponse.getOrderId());
        response.setOriginalPrice(trialResult.getOriginalPrice());
        response.setPayPrice(lockResponse.getLockAmount());
        response.setDeductionPrice(trialResult.getOriginalPrice().subtract(lockResponse.getLockAmount()));
        response.setTradeOrderStatus(0);
        response.setTeamId(lockResponse.getTeamId());
        return response;
    }

    public SettlementMarketPayOrderResponse settlementMarketPayOrder(SettlementMarketPayOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "outTradeNo cannot be blank");
        }
        String orderId = resolveOrderId(request.getOutTradeNo());
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
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
        GroupBuyTrialResult trialResult = groupBuyMarketTrialService.trial(toTrialCommand(request));

        GoodsMarketResponse response = new GoodsMarketResponse();
        response.setActivityId(trialResult.getActivityId());
        response.setVisible(trialResult.isVisible());
        response.setEnable(trialResult.isEnable());
        response.setMessage(trialResult.getMessage());
        response.setDiscount(toDiscount(trialResult));
        GoodsMarketResponse.Goods goods = new GoodsMarketResponse.Goods();
        goods.setGoodsId(trialResult.getGoodsId());
        goods.setGoodsName(trialResult.getGoodsName());
        goods.setOriginalPrice(trialResult.getOriginalPrice());
        goods.setPayPrice(trialResult.getPayPrice());
        goods.setDeductionPrice(trialResult.getDeductionPrice());
        response.setGoods(goods);
        fillTeamInfo(response, trialResult, request.getUserId());
        return response;
    }

    private LockGroupBuyOrderRequest toGroupBuyRequest(LockMarketPayOrderRequest request, GroupBuyTrialResult trialResult) {
        LockGroupBuyOrderRequest groupRequest = new LockGroupBuyOrderRequest();
        groupRequest.setUserId(request.getUserId());
        groupRequest.setGoodsId(request.getGoodsId());
        groupRequest.setActivityId(request.getActivityId());
        groupRequest.setTeamId(request.getTeamId());
        groupRequest.setIdempotentKey(resolveIdempotentKey(request));
        groupRequest.setPayChannel("MOCK_PAY");
        groupRequest.setGoodsName(trialResult.getGoodsName());
        groupRequest.setOriginalAmount(trialResult.getOriginalPrice());
        groupRequest.setPayAmount(trialResult.getPayPrice());
        return groupRequest;
    }

    private GroupBuyMarketTrialCommand toTrialCommand(LockMarketPayOrderRequest request) {
        GroupBuyMarketTrialCommand command = new GroupBuyMarketTrialCommand();
        command.setUserId(request.getUserId());
        command.setGoodsId(request.getGoodsId());
        command.setSource(request.getSource());
        command.setChannel(request.getChannel());
        command.setActivityId(request.getActivityId());
        return command;
    }

    private GroupBuyMarketTrialCommand toTrialCommand(GoodsMarketRequest request) {
        GroupBuyMarketTrialCommand command = new GroupBuyMarketTrialCommand();
        command.setUserId(request.getUserId());
        command.setGoodsId(request.getGoodsId());
        command.setSource(request.getSource());
        command.setChannel(request.getChannel());
        return command;
    }

    private GoodsMarketResponse.Discount toDiscount(GroupBuyTrialResult trialResult) {
        if (!StringUtils.hasText(trialResult.getDiscountId())) {
            return null;
        }
        GoodsMarketResponse.Discount discount = new GoodsMarketResponse.Discount();
        discount.setDiscountId(trialResult.getDiscountId());
        discount.setDiscountName(trialResult.getDiscountName());
        discount.setMarketPlan(trialResult.getMarketPlan());
        discount.setMarketExpr(trialResult.getMarketExpr());
        discount.setTagId(trialResult.getTagId());
        discount.setTagScope(trialResult.getTagScope());
        return discount;
    }

    private void fillTeamInfo(GoodsMarketResponse response, GroupBuyTrialResult trialResult, String userId) {
        if (!StringUtils.hasText(trialResult.getActivityId())) {
            return;
        }
        GroupBuyTeamStatistic statistic = groupBuyOrderLockRepository.queryTeamStatisticByActivityId(trialResult.getActivityId());
        response.getTeamStatistic().setAllTeamCount(statistic.getAllTeamCount());
        response.getTeamStatistic().setAllTeamCompleteCount(statistic.getAllTeamCompleteCount());
        response.getTeamStatistic().setAllTeamUserCount(statistic.getAllTeamUserCount());
        for (GroupBuyTeamDetail detail : groupBuyOrderLockRepository.queryInProgressTeamDetails(
                trialResult.getActivityId(), userId, 2, 3)) {
            response.getTeamList().add(toTeam(detail));
        }
    }

    private GoodsMarketResponse.Team toTeam(GroupBuyTeamDetail detail) {
        GoodsMarketResponse.Team team = new GoodsMarketResponse.Team();
        team.setUserId(detail.getUserId());
        team.setTeamId(detail.getTeamId());
        team.setActivityId(detail.getActivityId());
        team.setTargetCount(detail.getTargetCount());
        team.setCompleteCount(detail.getCompleteCount());
        team.setLockCount(detail.getLockCount());
        team.setValidStartTime(detail.getValidStartTime());
        team.setValidEndTime(detail.getValidEndTime());
        team.setOutTradeNo(detail.getOutTradeNo());
        return team;
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
