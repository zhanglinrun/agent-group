package com.linrun.trigger.http.groupbuy;




import com.linrun.domain.trade.service.TradeCompensationService;
import com.linrun.domain.groupbuy.service.GroupBuyLockOrderService;
import com.linrun.domain.groupbuy.service.GroupBuySettlementService;
import com.linrun.api.dto.LockGroupBuyOrderRequest;
import com.linrun.api.dto.LockGroupBuyOrderResponse;
import com.linrun.api.dto.GoodsMarketRequest;
import com.linrun.api.dto.LockMarketPayOrderRequest;
import com.linrun.api.dto.RefundMarketPayOrderRequest;
import com.linrun.api.dto.SettlementMarketPayOrderRequest;
import com.linrun.api.dto.GoodsMarketResponse;
import com.linrun.api.dto.LockMarketPayOrderResponse;
import com.linrun.api.dto.RefundMarketPayOrderResponse;
import com.linrun.api.dto.SettlementMarketPayOrderResponse;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyProgress;
import com.linrun.domain.groupbuy.model.GroupBuyTeamDetail;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatistic;
import com.linrun.domain.groupbuy.model.GroupBuyTrialResult;
import com.linrun.domain.groupbuy.service.GroupBuyMarketTrialService;
import com.linrun.trigger.http.agent.HumanApprovalHandler;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class MarketTradeFacadeHandler {

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyLockOrderService groupBuyLockOrderService;
    private final GroupBuySettlementService groupBuySettlementService;
    private final TradeCompensationService tradeCompensationService;
    private final TradeOrderRepository tradeOrderRepository;
    private final DynamicConfigService dynamicConfigService;
    private final GroupBuyMarketTrialService groupBuyMarketTrialService;
    private final HumanApprovalHandler humanApprovalService;

    public MarketTradeFacadeHandler(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyLockOrderService groupBuyLockOrderService,
                                    GroupBuySettlementService groupBuySettlementService,
                                    TradeCompensationService tradeCompensationService,
                                    TradeOrderRepository tradeOrderRepository,
                                    DynamicConfigService dynamicConfigService,
                                    GroupBuyMarketTrialService groupBuyMarketTrialService,
                                    HumanApprovalHandler humanApprovalService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyLockOrderService = groupBuyLockOrderService;
        this.groupBuySettlementService = groupBuySettlementService;
        this.tradeCompensationService = tradeCompensationService;
        this.tradeOrderRepository = tradeOrderRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.groupBuyMarketTrialService = groupBuyMarketTrialService;
        this.humanApprovalService = humanApprovalService == null ? new HumanApprovalHandler() : humanApprovalService;
    }

    public LockMarketPayOrderResponse lockMarketPayOrder(LockMarketPayOrderRequest request) {
        validateLockRequest(request);
        GroupBuyTrialResult trialResult = groupBuyMarketTrialService.trial(toTrialCommand(request));
        if (!request.getActivityId().equals(trialResult.getActivityId())) {
            throw new AppException("GROUP_0018", "????????????????");
        }
        if (!trialResult.isEnable() || !trialResult.isAvailable()) {
            throw new AppException("GROUP_0019", "???????????????");
        }
        humanApprovalService.assertApproved(request.getHitlApprovalId(), request.getUserId(),
                HumanApprovalHandler.ACTION_LOCK_MARKET_PAY_ORDER, request.getOutTradeNo());
        LockGroupBuyOrderResponse lockResponse = groupBuyLockOrderService.lock(toGroupBuyRequest(request));

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
            throw new AppException("0001", "?????????");
        }
        humanApprovalService.assertApproved(request.getHitlApprovalId(), request.getUserId(),
                HumanApprovalHandler.ACTION_SETTLEMENT_MARKET_PAY_ORDER, request.getOutTradeNo());
        String orderId = resolveOrderId(request.getOutTradeNo());
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "?????"));

        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "pay order not found"));
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            throw new AppException("GROUP_0023", "only group buy order can be settled by market facade");
        }
        if (!PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            throw new AppException("GROUP_0024", "order payment is not success yet");
        }
        groupBuySettlementService.settlePaySuccess(tradeOrder);

        GroupBuyOrderLock orderLock = groupBuyOrderLockRepository.queryLockByOrderId(orderId)
                .orElseThrow(() -> new AppException("GROUP_0011", "???????"));

        SettlementMarketPayOrderResponse response = new SettlementMarketPayOrderResponse();
        response.setUserId(tradeOrder.getUserId());
        response.setTeamId(orderLock.getTeamId());
        response.setActivityId(orderLock.getActivityId());
        response.setOutTradeNo(request.getOutTradeNo());
        return response;
    }

    public RefundMarketPayOrderResponse refundMarketPayOrder(RefundMarketPayOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "?????????");
        }
        humanApprovalService.assertApproved(request.getHitlApprovalId(), request.getUserId(),
                HumanApprovalHandler.ACTION_REFUND_MARKET_PAY_ORDER, request.getOutTradeNo());
        String orderId = resolveOrderId(request.getOutTradeNo());
        boolean success = tradeCompensationService.refundOrCloseOrder(
                request.getUserId(), orderId, "????");
        GroupBuyOrderLock orderLock = groupBuyOrderLockRepository.queryLockByOrderId(orderId).orElse(null);

        RefundMarketPayOrderResponse response = new RefundMarketPayOrderResponse();
        response.setUserId(request.getUserId());
        response.setOrderId(orderId);
        response.setTeamId(orderLock == null ? null : orderLock.getTeamId());
        response.setCode(success ? "0000" : "0002");
        response.setInfo(success ? "????" : "????");
        return response;
    }

    public GoodsMarketResponse queryGroupBuyMarketConfig(GoodsMarketRequest request) {
        if (request == null || !StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "?????????");
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
        goods.setTotalStock(trialResult.getTotalStock());
        goods.setAvailableStock(trialResult.getAvailableStock());
        goods.setLockedStock(trialResult.getLockedStock());
        goods.setPaidStock(trialResult.getPaidStock());
        response.setGoods(goods);
        fillTeamInfo(response, trialResult, request.getUserId());
        return response;
    }

    private LockGroupBuyOrderRequest toGroupBuyRequest(LockMarketPayOrderRequest request) {
        LockGroupBuyOrderRequest groupRequest = new LockGroupBuyOrderRequest();
        groupRequest.setUserId(request.getUserId());
        groupRequest.setGoodsId(request.getGoodsId());
        groupRequest.setDecisionId(request.getDecisionId());
        groupRequest.setActivityId(request.getActivityId());
        groupRequest.setTeamId(request.getTeamId());
        groupRequest.setIdempotentKey(resolveIdempotentKey(request));
        groupRequest.setPayChannel(StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : "ALIPAY");
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
        team.setProgress(toProgress(GroupBuyProgress.fromTeamDetail(detail)));
        team.setValidStartTime(detail.getValidStartTime());
        team.setValidEndTime(detail.getValidEndTime());
        team.setOutTradeNo(detail.getOutTradeNo());
        return team;
    }

    private GoodsMarketResponse.GroupProgress toProgress(GroupBuyProgress progress) {
        GoodsMarketResponse.GroupProgress dto = new GoodsMarketResponse.GroupProgress();
        dto.setTargetCount(progress.getTargetCount());
        dto.setLockedCount(progress.getLockedCount());
        dto.setCompleteCount(progress.getCompleteCount());
        dto.setRemainingCount(progress.getRemainingCount());
        dto.setProgressRate(progress.getProgressRate());
        dto.setSuccess(progress.isSuccess());
        return dto;
    }

    private void validateLockRequest(LockMarketPayOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "????????");
        }
        if (!StringUtils.hasText(request.getUserId())
                || !StringUtils.hasText(request.getGoodsId())
                || !StringUtils.hasText(request.getActivityId())
                || !StringUtils.hasText(request.getOutTradeNo())) {
            throw new AppException("0001", "???????????????????");
        }
        if (dynamicConfigService.isDowngradeSwitch()) {
            throw new AppException("DCC_0003", "?????????");
        }
        if (!dynamicConfigService.isCutRange(request.getUserId())) {
            throw new AppException("DCC_0004", "????????????");
        }
        if (dynamicConfigService.isSourceChannelBlackIntercept(request.getSource(), request.getChannel())) {
            throw new AppException("DCC_0005", "???????????");
        }
    }

    private String resolveIdempotentKey(LockMarketPayOrderRequest request) {
        return StringUtils.hasText(request.getOutTradeNo())
                ? request.getOutTradeNo()
                : request.getUserId() + ":" + request.getGoodsId() + ":" + request.getActivityId();
    }

    private GuideProduct queryProduct(String goodsId) {
        GuideProduct product = guideDataRepository.queryProductByGoodsId(goodsId)
                .orElseThrow(() -> new AppException("DATA_0003", "额度包不存在或已下架"));
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
