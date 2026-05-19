package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.CreateTradeOrderCommand;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.model.TradePayOrder;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class GroupBuyLockOrderService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_PAY_CHANNEL = "MOCK_PAY";

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository,
                GroupBuyStockRepository.noop(), tradeOrderRepository, tradeOrderService, tradeStatusFlowService);
    }

    @Autowired
    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    @Transactional(rollbackFor = Exception.class)
    public LockGroupBuyOrderResponse lock(LockGroupBuyOrderRequest request) {
        validate(request);

        GroupBuyOrderLock repeatedLock = groupBuyOrderLockRepository.queryLockByIdempotentKey(request.getIdempotentKey())
                .orElse(null);
        if (repeatedLock != null) {
            GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(repeatedLock.getTeamId())
                    .orElseThrow(() -> new AppException("GROUP_0009", "拼团锁单数据不完整"));
            TradePayOrder tradePayOrder = queryTradePayOrder(repeatedLock.getOrderId());
            return toResponse(new GroupBuyLockResult(repeatedLock, team, true), tradePayOrder);
        }

        GuideProduct product = guideDataRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "商品不存在或已下架"));
        GroupBuyActivity activity = groupBuyActivityRepository.queryByActivityId(request.getActivityId())
                .orElseThrow(() -> new AppException("GROUP_0001", "拼团活动不存在"));

        LocalDateTime now = LocalDateTime.now();
        validateActivity(request, activity, now);

        String teamId = StringUtils.hasText(request.getTeamId()) ? request.getTeamId() : nextNo("T");
        GroupBuyOrderLock orderLock = GroupBuyOrderLock.locked(
                nextNo("L"),
                request.getIdempotentKey(),
                request.getUserId(),
                teamId,
                activity,
                now);
        TradePayOrder tradePayOrder = createTradePayOrder(request, product, activity);
        orderLock.setOrderId(tradePayOrder.getTradeOrder().getOrderId());
        groupBuyStockRepository.lockStock(activity.getActivityId(), activity.getGoodsId(),
                tradePayOrder.getTradeOrder().getOrderId(), teamId);

        if (!StringUtils.hasText(request.getTeamId())) {
            GroupBuyTeam team = GroupBuyTeam.create(teamId, activity, now);
            GroupBuyLockResult lockResult = groupBuyOrderLockRepository.lockNewTeam(team, orderLock);
            tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
            recordLockFlow(lockResult.getOrderLock(), tradePayOrder);
            return toResponse(lockResult, tradePayOrder);
        }

        GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(teamId)
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), now);
        GroupBuyLockResult lockResult = groupBuyOrderLockRepository.lockExistingTeam(orderLock);
        tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
        recordLockFlow(lockResult.getOrderLock(), tradePayOrder);
        return toResponse(lockResult, tradePayOrder);
    }

    private void recordLockFlow(GroupBuyOrderLock orderLock, TradePayOrder tradePayOrder) {
        TradeOrder tradeOrder = tradePayOrder.getTradeOrder();
        PayOrder payOrder = tradePayOrder.getPayOrder();
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_GROUP,
                orderLock.getLockId(),
                TradeStatusFlowService.EVENT_GROUP_LOCKED,
                null,
                orderLock.getLockStatus(),
                "group slot locked");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_CREATE_GROUP_ORDER,
                null,
                tradeOrder.getOrderStatus(),
                "group order created");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_CREATE_PAY_ORDER,
                null,
                payOrder.getPayStatus(),
                "pay order created");
    }

    private void validate(LockGroupBuyOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "锁单参数不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "商品编号不能为空");
        }
        if (!StringUtils.hasText(request.getActivityId())) {
            throw new AppException("0001", "活动编号不能为空");
        }
        if (!StringUtils.hasText(request.getIdempotentKey())) {
            throw new AppException("0001", "幂等键不能为空");
        }
    }

    private void validateActivity(LockGroupBuyOrderRequest request, GroupBuyActivity activity, LocalDateTime now) {
        if (!request.getGoodsId().equals(activity.getGoodsId())) {
            throw new AppException("GROUP_0002", "拼团活动和商品不匹配");
        }
        if (!GroupBuyActivityStatus.ACTIVE.equals(activity.resolveStatus(now))) {
            throw new AppException("GROUP_0008", "拼团活动不可用");
        }
    }

    private TradePayOrder createTradePayOrder(LockGroupBuyOrderRequest request, GuideProduct product, GroupBuyActivity activity) {
        CreateTradeOrderCommand command = new CreateTradeOrderCommand();
        command.setUserId(request.getUserId());
        command.setGoodsId(product.getGoodsId());
        command.setGoodsName(product.getGoodsName());
        command.setActivityId(activity.getActivityId());
        command.setBuyType(TradeBuyType.GROUP_BUY);
        command.setOriginAmount(product.getOriginPrice());
        command.setPayAmount(activity.getGroupPrice());

        TradeOrder tradeOrder = tradeOrderService.createOrder(command);
        return tradeOrderService.createPayOrder(tradeOrder, resolvePayChannel(request));
    }

    private TradePayOrder queryTradePayOrder(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("GROUP_0010", "拼团锁单未关联交易订单");
        }
        TradeOrder tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrder payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));

        TradePayOrder tradePayOrder = new TradePayOrder();
        tradePayOrder.setTradeOrder(tradeOrder);
        tradePayOrder.setPayOrder(payOrder);
        return tradePayOrder;
    }

    private String resolvePayChannel(LockGroupBuyOrderRequest request) {
        return StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : DEFAULT_PAY_CHANNEL;
    }

    private LockGroupBuyOrderResponse toResponse(GroupBuyLockResult result, TradePayOrder tradePayOrder) {
        GroupBuyOrderLock orderLock = result.getOrderLock();
        GroupBuyTeam team = result.getTeam();
        TradeOrder tradeOrder = tradePayOrder.getTradeOrder();
        PayOrder payOrder = tradePayOrder.getPayOrder();

        LockGroupBuyOrderResponse response = new LockGroupBuyOrderResponse();
        response.setLockId(orderLock.getLockId());
        response.setUserId(orderLock.getUserId());
        response.setGoodsId(orderLock.getGoodsId());
        response.setActivityId(orderLock.getActivityId());
        response.setTeamId(orderLock.getTeamId());
        response.setTeamSize(team.getTargetCount());
        response.setLockedCount(team.getLockCount());
        response.setRemainingCount(team.remainingCount());
        response.setTeamStatus(team.getTeamStatus().name());
        response.setLockStatus(orderLock.getLockStatus().name());
        response.setLockAmount(orderLock.getLockAmount());
        response.setLockTime(orderLock.getLockTime());
        response.setRepeated(result.isRepeated());
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setPayUrl(payOrder.getPayUrl());
        return response;
    }

    private String nextNo(String prefix) {
        String timePart = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + timePart + randomPart;
    }
}
