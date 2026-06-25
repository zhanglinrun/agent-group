package com.linrun.domain.groupbuy.service;

import com.linrun.api.dto.LockGroupBuyOrderRequest;
import com.linrun.api.dto.LockGroupBuyOrderResponse;
import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.agent.conversation.adapter.QuotaOrderSnapshotRepository;
import com.linrun.domain.agent.conversation.adapter.QuotaProductRepository;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.conversation.service.QuotaOrderSnapshotValidator;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.CreateTradeOrderCommandEntity;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.aggregate.TradePayOrderAggregate;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.domain.groupbuy.service.rules.lock.GroupBuyLockContext;
import com.linrun.domain.groupbuy.service.rules.lock.GroupBuyLockRuleChain;
import com.linrun.domain.support.lock.DistributedLock;
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
    private static final String DEFAULT_PAY_CHANNEL = "ALIPAY";

    private final QuotaProductRepository quotaProductRepository;
    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final GroupBuyTeamStockRepository groupBuyTeamStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final QuotaOrderSnapshotValidator quotaOrderSnapshotValidator;
    private final AgentObservabilityMetrics metrics;
    private final GroupBuyLockRuleChain groupBuyLockRuleChain;
    private final PaymentService paymentService;

    public GroupBuyLockOrderService(QuotaProductRepository quotaProductRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    QuotaOrderSnapshotRepository quotaOrderSnapshotRepository) {
        this(quotaProductRepository, groupBuyActivityRepository, groupBuyOrderLockRepository, groupBuyStockRepository,
                groupBuyTeamStockRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                new QuotaOrderSnapshotValidator(quotaOrderSnapshotRepository), AgentObservabilityMetrics.noop(), null);
    }

    @Autowired
    public GroupBuyLockOrderService(QuotaProductRepository quotaProductRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                    GroupBuyStockRepository groupBuyStockRepository,
                                    GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                    TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    QuotaOrderSnapshotValidator quotaOrderSnapshotValidator,
                                    AgentObservabilityMetrics metrics,
                                    PaymentService paymentService) {
        this.quotaProductRepository = quotaProductRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.groupBuyTeamStockRepository = groupBuyTeamStockRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.quotaOrderSnapshotValidator = quotaOrderSnapshotValidator;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
        this.paymentService = paymentService;
        this.groupBuyLockRuleChain = new GroupBuyLockRuleChain(
                groupBuyOrderLockRepository,
                groupBuyTeamStockRepository,
                quotaOrderSnapshotValidator);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributedLock(key = "'group-buy:lock:' + #p0.userId + ':' + #p0.activityId + ':' + #p0.idempotentKey",
            waitTime = 1L, leaseTime = 30L)
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
            CreatePaymentResponse payment = createGatewayPayment(
                    tradePayOrder.getTradeOrder(),
                    tradePayOrder.getPayOrder(),
                    request);
            return toResponse(new GroupBuyLockResult(repeatedLock, team, true),
                    tradePayOrder,
                    request.getDecisionId(),
                    payment);
        }

        QuotaProduct product = quotaProductRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "额度包不存在或已下架"));
        GroupBuyActivity activity = groupBuyActivityRepository.queryByActivityId(request.getActivityId())
                .orElseThrow(() -> new AppException("GROUP_0001", "拼团活动不存在"));

        LocalDateTime now = LocalDateTime.now();
        GroupBuyLockContext lockContext = new GroupBuyLockContext(request, product, activity, now);
        groupBuyLockRuleChain.apply(lockContext);

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
            CreatePaymentResponse payment = createGatewayPayment(
                    tradePayOrder.getTradeOrder(),
                    tradePayOrder.getPayOrder(),
                    request);
            return toResponse(lockResult, tradePayOrder, request.getDecisionId(), payment);
        }

        GroupBuyTeam team = lockContext.getTeam();
        if (team == null) {
            throw new AppException("GROUP_0003", "拼团队伍不存在");
        }
        try {
            groupBuyStockRepository.lockStock(activity.getActivityId(), activity.getGoodsId(),
                    tradePayOrder.getTradeOrder().getOrderId(), teamId);
            GroupBuyLockResult lockResult = groupBuyOrderLockRepository.lockExistingTeam(orderLock);
            tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
            recordLockFlow(lockResult.getOrderLock(), tradePayOrder);
            CreatePaymentResponse payment = createGatewayPayment(
                    tradePayOrder.getTradeOrder(),
                    tradePayOrder.getPayOrder(),
                    request);
            return toResponse(lockResult, tradePayOrder, request.getDecisionId(), payment);
        } catch (RuntimeException e) {
            if (lockContext.isTeamStockOccupied()) {
                groupBuyTeamStockRepository.recoverTeamStock(
                        activity.getActivityId(), teamId, orderLock.getOrderId(), team.getValidEndTime());
            }
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
            throw new AppException("0001", "额度包编号不能为空");
        }
        if (!StringUtils.hasText(request.getActivityId())) {
            throw new AppException("0001", "活动编号不能为空");
        }
        if (!StringUtils.hasText(request.getIdempotentKey())) {
            throw new AppException("0001", "幂等键不能为空");
        }
    }

    private void validateRepeatedLock(LockGroupBuyOrderRequest request, GroupBuyOrderLock repeatedLock) {
        if (!request.getUserId().equals(repeatedLock.getUserId())
                || !request.getGoodsId().equals(repeatedLock.getGoodsId())
                || !request.getActivityId().equals(repeatedLock.getActivityId())) {
            throw new AppException("GROUP_0020", "请勿重复提交不同的拼团订单");
        }
    }

    private TradePayOrderAggregate createTradePayOrder(LockGroupBuyOrderRequest request,
                                              QuotaProduct product,
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

    private CreatePaymentResponse createGatewayPayment(TradeOrderEntity tradeOrder,
                                                       PayOrderEntity payOrder,
                                                       LockGroupBuyOrderRequest request) {
        if (paymentService == null || payOrder == null || !PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())) {
            return null;
        }
        if (StringUtils.hasText(payOrder.getPayUrl())) {
            return null;
        }
        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(tradeOrder.getOrderId());
        paymentRequest.setPayChannel(resolvePayChannel(request));
        return paymentService.createPayment(paymentRequest, tradeOrder.getUserId());
    }

    private LockGroupBuyOrderResponse toResponse(GroupBuyLockResult result,
                                                 TradePayOrderAggregate tradePayOrder,
                                                 String decisionId,
                                                 CreatePaymentResponse payment) {
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
        response.setPayUrl(payment == null ? payOrder.getPayUrl() : payment.getPayUrl());
        response.setPayFormHtml(payment == null ? resolvePayFormHtml(payOrder.getPayUrl()) : payment.getPayFormHtml());
        response.setPaymentType(payment == null ? resolvePaymentType(payOrder.getPayUrl()) : payment.getPaymentType());
        response.setPayChannel(payment == null ? payOrder.getPayChannel() : payment.getPayChannel());
        response.setGatewayTradeNo(payment == null ? payOrder.getOutTradeNo() : payment.getGatewayTradeNo());
        return response;
    }

    private String resolvePayFormHtml(String payUrl) {
        return looksLikePaymentForm(payUrl) ? payUrl : null;
    }

    private String resolvePaymentType(String payUrl) {
        return looksLikePaymentForm(payUrl) ? "PAGE_FORM" : "URL";
    }

    private boolean looksLikePaymentForm(String value) {
        return StringUtils.hasText(value) && value.toLowerCase().contains("<form");
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















