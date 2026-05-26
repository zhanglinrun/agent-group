package com.linrun.trigger.service;

import com.linrun.api.marketing.request.LockGroupBuyOrderRequest;
import com.linrun.api.marketing.response.LockGroupBuyOrderResponse;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.marketing.adapter.GroupBuyStockRepository;
import com.linrun.domain.marketing.adapter.GroupBuyTeamStockRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyActivityStatus;
import com.linrun.domain.marketing.model.GroupBuyLockResult;
import com.linrun.domain.marketing.model.GroupBuyOrderLock;
import com.linrun.domain.marketing.model.GroupBuyTeam;
import com.linrun.domain.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.CreateTradeOrderCommandEntity;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.aggregate.TradePayOrderAggregate;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
    private final GroupBuyTeamStockRepository groupBuyTeamStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final GuideDecisionSnapshotValidator guideDecisionSnapshotValidator;
    private final AgentObservabilityMetrics metrics;

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository,
                GroupBuyStockRepository.noop(), GroupBuyTeamStockRepository.noop(),
                tradeOrderRepository, tradeOrderService, tradeStatusFlowService);
    }

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository,
                groupBuyStockRepository, GroupBuyTeamStockRepository.noop(),
                tradeOrderRepository, tradeOrderService, tradeStatusFlowService);
    }

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository, groupBuyStockRepository,
                groupBuyTeamStockRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                GuideDecisionSnapshotRepository.noop());
    }

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    GuideDecisionSnapshotRepository guideDecisionSnapshotRepository) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository, groupBuyStockRepository,
                groupBuyTeamStockRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                new GuideDecisionSnapshotValidator(guideDecisionSnapshotRepository));
    }

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    GuideDecisionSnapshotValidator guideDecisionSnapshotValidator) {
        this(guideDataRepository, groupBuyActivityRepository, groupBuyOrderLockRepository, groupBuyStockRepository,
                groupBuyTeamStockRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                guideDecisionSnapshotValidator, AgentObservabilityMetrics.noop());
    }

    @Autowired
    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    GuideDecisionSnapshotValidator guideDecisionSnapshotValidator,
                                    AgentObservabilityMetrics metrics) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.groupBuyTeamStockRepository = groupBuyTeamStockRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.guideDecisionSnapshotValidator = guideDecisionSnapshotValidator;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
    }

    @Transactional(rollbackFor = Exception.class)
    public LockGroupBuyOrderResponse lock(LockGroupBuyOrderRequest request) {
        long startNanos = System.nanoTime();
        try {
            LockGroupBuyOrderResponse response = doLock(request);
            metrics.recordGroupBuyLock(activityTag(request),
                    response.isRepeated() ? "repeated" : "success",
                    elapsedMillis(startNanos));
            return response;
        } catch (RuntimeException e) {
            metrics.recordGroupBuyLock(activityTag(request), failureStatus(e), elapsedMillis(startNanos));
            throw e;
        }
    }

    private LockGroupBuyOrderResponse doLock(LockGroupBuyOrderRequest request) {
        validate(request);

        GroupBuyOrderLock repeatedLock = groupBuyOrderLockRepository.queryLockByIdempotentKey(request.getIdempotentKey())
                .orElse(null);
        if (repeatedLock != null) {
            validateRepeatedLock(request, repeatedLock);
            GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(repeatedLock.getTeamId())
                    .orElseThrow(() -> new AppException("GROUP_0009", "拼团锁单数据不完整"));
            TradePayOrderAggregate tradePayOrder = queryTradePayOrder(repeatedLock.getOrderId());
            return toResponse(new GroupBuyLockResult(repeatedLock, team, true), tradePayOrder, request.getDecisionId());
        }

        GuideProduct product = guideDataRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "商品不存在或已下架"));
        GroupBuyActivity activity = groupBuyActivityRepository.queryByActivityId(request.getActivityId())
                .orElseThrow(() -> new AppException("GROUP_0001", "拼团活动不存在"));

        LocalDateTime now = LocalDateTime.now();
        validateActivity(request, activity, now);
        validateTakeLimit(request, activity);
        guideDecisionSnapshotValidator.validateGroup(
                request.getDecisionId(),
                request.getUserId(),
                request.getGoodsId(),
                request.getActivityId(),
                product.getOriginPrice(),
                activity.getGroupPrice(),
                now);

        String teamId = StringUtils.hasText(request.getTeamId()) ? request.getTeamId() : nextNo("T");
        BigDecimal payAmount = resolvePayAmount(activity);
        GroupBuyOrderLock orderLock = GroupBuyOrderLock.locked(
                nextNo("L"),
                request.getIdempotentKey(),
                request.getUserId(),
                teamId,
                activity,
                now);
        orderLock.setLockAmount(payAmount);
        TradePayOrderAggregate tradePayOrder = createTradePayOrder(request, product, activity, payAmount);
        orderLock.setOrderId(tradePayOrder.getTradeOrder().getOrderId());

        if (!StringUtils.hasText(request.getTeamId())) {
            groupBuyStockRepository.lockStock(activity.getActivityId(), activity.getGoodsId(),
                    tradePayOrder.getTradeOrder().getOrderId(), teamId);
            GroupBuyTeam team = GroupBuyTeam.create(teamId, activity, now);
            GroupBuyLockResult lockResult = groupBuyOrderLockRepository.lockNewTeam(team, orderLock);
            tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
            recordLockFlow(lockResult.getOrderLock(), tradePayOrder);
            return toResponse(lockResult, tradePayOrder, request.getDecisionId());
        }

        GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(teamId)
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), now);
        boolean teamStockOccupied = groupBuyTeamStockRepository.occupyTeamStock(
                activity.getActivityId(), teamId, team.getTargetCount(), team.getValidEndTime());
        if (!teamStockOccupied) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
        try {
            groupBuyStockRepository.lockStock(activity.getActivityId(), activity.getGoodsId(),
                    tradePayOrder.getTradeOrder().getOrderId(), teamId);
            GroupBuyLockResult lockResult = groupBuyOrderLockRepository.lockExistingTeam(orderLock);
            tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
            recordLockFlow(lockResult.getOrderLock(), tradePayOrder);
            return toResponse(lockResult, tradePayOrder, request.getDecisionId());
        } catch (RuntimeException e) {
            groupBuyTeamStockRepository.recoverTeamStock(
                    activity.getActivityId(), teamId, orderLock.getOrderId(), team.getValidEndTime());
            throw e;
        }
    }

    private void recordLockFlow(GroupBuyOrderLock orderLock, TradePayOrderAggregate tradePayOrder) {
        TradeOrderEntity tradeOrder = tradePayOrder.getTradeOrder();
        PayOrderEntity payOrder = tradePayOrder.getPayOrder();
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
        if (!StringUtils.hasText(request.getDecisionId())) {
            throw new AppException("GUIDE_0005", "导购决策编号不能为空，请先完成导购推荐后再下单");
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

    private void validateTakeLimit(LockGroupBuyOrderRequest request, GroupBuyActivity activity) {
        Integer takeLimitCount = activity.getTakeLimitCount();
        if (takeLimitCount == null || takeLimitCount <= 0) {
            return;
        }
        int count = groupBuyOrderLockRepository.countUserActivityLocks(request.getUserId(), activity.getActivityId());
        if (count >= takeLimitCount) {
            throw new AppException("GROUP_0017", "user group buy take limit reached");
        }
    }

    private void validateRepeatedLock(LockGroupBuyOrderRequest request, GroupBuyOrderLock repeatedLock) {
        if (!request.getUserId().equals(repeatedLock.getUserId())
                || !request.getGoodsId().equals(repeatedLock.getGoodsId())
                || !request.getActivityId().equals(repeatedLock.getActivityId())) {
            throw new AppException("GROUP_0020", "idempotent key conflict");
        }
    }

    private TradePayOrderAggregate createTradePayOrder(LockGroupBuyOrderRequest request,
                                              GuideProduct product,
                                              GroupBuyActivity activity,
                                              BigDecimal payAmount) {
        CreateTradeOrderCommandEntity command = new CreateTradeOrderCommandEntity();
        command.setUserId(request.getUserId());
        command.setGoodsId(product.getGoodsId());
        command.setGoodsName(product.getGoodsName());
        command.setIdempotentKey(request.getIdempotentKey());
        command.setActivityId(activity.getActivityId());
        command.setBuyType(TradeBuyTypeEnumVO.GROUP_BUY);
        command.setOriginAmount(product.getOriginPrice());
        command.setPayAmount(payAmount);

        TradeOrderEntity tradeOrder = tradeOrderService.createOrder(command);
        return tradeOrderService.createPayOrder(tradeOrder, resolvePayChannel(request));
    }

    private BigDecimal resolvePayAmount(GroupBuyActivity activity) {
        if (activity.getGroupPrice() != null) {
            return activity.getGroupPrice();
        }
        return BigDecimal.ZERO;
    }

    private TradePayOrderAggregate queryTradePayOrder(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("GROUP_0010", "拼团锁单未关联交易订单");
        }
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));

        TradePayOrderAggregate tradePayOrder = new TradePayOrderAggregate();
        tradePayOrder.setTradeOrder(tradeOrder);
        tradePayOrder.setPayOrder(payOrder);
        return tradePayOrder;
    }

    private String resolvePayChannel(LockGroupBuyOrderRequest request) {
        return StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : DEFAULT_PAY_CHANNEL;
    }

    private LockGroupBuyOrderResponse toResponse(GroupBuyLockResult result,
                                                 TradePayOrderAggregate tradePayOrder,
                                                 String decisionId) {
        GroupBuyOrderLock orderLock = result.getOrderLock();
        GroupBuyTeam team = result.getTeam();
        TradeOrderEntity tradeOrder = tradePayOrder.getTradeOrder();
        PayOrderEntity payOrder = tradePayOrder.getPayOrder();

        LockGroupBuyOrderResponse response = new LockGroupBuyOrderResponse();
        response.setLockId(orderLock.getLockId());
        response.setDecisionId(decisionId);
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

    private String activityTag(LockGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getActivityId())) {
            return "unknown";
        }
        return request.getActivityId();
    }

    private String failureStatus(RuntimeException exception) {
        if (exception instanceof AppException appException && StringUtils.hasText(appException.getCode())) {
            return appException.getCode();
        }
        return exception == null ? "failed" : exception.getClass().getSimpleName();
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
