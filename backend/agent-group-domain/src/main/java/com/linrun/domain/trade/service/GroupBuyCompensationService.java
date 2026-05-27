package com.linrun.domain.trade.service;

import com.linrun.api.dto.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.activity.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.activity.model.GroupBuyLockStatus;
import com.linrun.domain.activity.model.GroupBuySettlementResult;
import com.linrun.domain.activity.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class GroupBuyCompensationService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final GroupBuyTeamStockRepository groupBuyTeamStockRepository;
    private final TradeStatusFlowService tradeStatusFlowService;

    public GroupBuyCompensationService(TradeOrderRepository tradeOrderRepository,
                                       TradeOrderService tradeOrderService,
                                       GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, groupBuyOrderLockRepository,
                GroupBuyStockRepository.noop(), GroupBuyTeamStockRepository.noop(), tradeStatusFlowService);
    }

    public GroupBuyCompensationService(TradeOrderRepository tradeOrderRepository,
                                       TradeOrderService tradeOrderService,
                                       GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       GroupBuyStockRepository groupBuyStockRepository,
                                       TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, groupBuyOrderLockRepository,
                groupBuyStockRepository, GroupBuyTeamStockRepository.noop(), tradeStatusFlowService);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GroupBuyCompensationService(TradeOrderRepository tradeOrderRepository,
                                       TradeOrderService tradeOrderService,
                                       GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       GroupBuyStockRepository groupBuyStockRepository,
                                       GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                       TradeStatusFlowService tradeStatusFlowService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.groupBuyTeamStockRepository = groupBuyTeamStockRepository;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse closeUnpaid(CloseUnpaidGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        validateGroupBuyOrder(tradeOrder);

        TradeOrderStatusEnumVO fromOrderStatus = tradeOrder.getOrderStatus();
        PayStatusEnumVO fromPayStatus = payOrder.getPayStatus();
        LocalDateTime closeTime = request.getCloseTime() == null ? LocalDateTime.now() : request.getCloseTime();
        tradeOrderService.closeUnpaidOrder(tradeOrder, payOrder, closeTime);
        tradeOrderRepository.updateCloseUnpaid(tradeOrder, payOrder);
        GroupBuySettlementResult releaseResult = groupBuyOrderLockRepository.releaseLockedOrder(tradeOrder.getOrderId());
        if (!releaseResult.isRepeated()) {
            groupBuyStockRepository.releaseLockedStock(
                    releaseResult.getOrderLock().getActivityId(),
                    releaseResult.getOrderLock().getGoodsId(),
                    releaseResult.getOrderLock().getOrderId(),
                    releaseResult.getOrderLock().getTeamId());
            recoverTeamStock(releaseResult);
        }
        recordCloseFlow(tradeOrder, payOrder, releaseResult, fromOrderStatus, fromPayStatus);
        return toResponse(tradeOrder, payOrder, null, releaseResult, closeTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse refundUnsettled(RefundGroupBuyOrderRequest request) {
        return releaseRefundedOrder(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupBuyCompensationResponse releaseRefundedOrder(RefundGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = queryTradeOrder(request.getOrderId());
        PayOrderEntity payOrder = queryPayOrder(request.getOrderId());
        validateGroupBuyOrder(tradeOrder);

        RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(tradeOrder.getOrderId())
                .orElseThrow(() -> new AppException("TRADE_0015", "refund order not found"));
        GroupBuySettlementResult releaseResult = releasePaidGroupBuyOrder(tradeOrder.getOrderId());
        recordRefundReleaseFlow(tradeOrder, releaseResult);
        return toResponse(tradeOrder, payOrder, refundOrder, releaseResult, refundOrder.getRefundTime());
    }

    private void recordCloseFlow(TradeOrderEntity tradeOrder,
                                 PayOrderEntity payOrder,
                                 GroupBuySettlementResult releaseResult,
                                 TradeOrderStatusEnumVO fromOrderStatus,
                                 PayStatusEnumVO fromPayStatus) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_CLOSE_UNPAID,
                fromOrderStatus,
                tradeOrder.getOrderStatus(),
                "order closed");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_CLOSE_UNPAID,
                fromPayStatus,
                payOrder.getPayStatus(),
                "pay order closed");
        if (!releaseResult.isRepeated()) {
            tradeStatusFlowService.record(
                    tradeOrder.getOrderId(),
                    TradeStatusFlowService.BIZ_GROUP,
                    releaseResult.getOrderLock().getLockId(),
                    TradeStatusFlowService.EVENT_RELEASE_LOCK,
                    GroupBuyLockStatus.LOCKED,
                    releaseResult.getOrderLock().getLockStatus(),
                    "group lock released");
        }
    }

    private GroupBuySettlementResult releasePaidGroupBuyOrder(String orderId) {
        GroupBuySettlementResult releaseResult = groupBuyOrderLockRepository.releasePaidOrder(orderId);
        if (isNewPaidRelease(releaseResult)) {
            groupBuyStockRepository.releasePaidStock(
                    releaseResult.getOrderLock().getActivityId(),
                    releaseResult.getOrderLock().getGoodsId(),
                    releaseResult.getOrderLock().getOrderId(),
                    releaseResult.getOrderLock().getTeamId());
            recoverTeamStock(releaseResult);
        }
        return releaseResult;
    }

    private void recordRefundReleaseFlow(TradeOrderEntity tradeOrder,
                                         GroupBuySettlementResult releaseResult) {
        if (!isNewPaidRelease(releaseResult)) {
            return;
        }
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_GROUP,
                releaseResult.getOrderLock().getLockId(),
                TradeStatusFlowService.EVENT_RELEASE_PAID_LOCK,
                GroupBuyLockStatus.PAID,
                releaseResult.getOrderLock().getLockStatus(),
                "paid group lock released");
    }

    private boolean isNewPaidRelease(GroupBuySettlementResult releaseResult) {
        return !releaseResult.isRepeated()
                && GroupBuyLockStatus.RELEASED.equals(releaseResult.getOrderLock().getLockStatus());
    }

    private void validateGroupBuyOrder(TradeOrderEntity tradeOrder) {
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            throw new AppException("TRADE_0008", "非拼团订单不能做拼团补偿");
        }
    }

    private void recoverTeamStock(GroupBuySettlementResult releaseResult) {
        if (GroupBuyTeamStatus.SUCCESS.equals(releaseResult.getTeam().getTeamStatus())) {
            return;
        }
        groupBuyTeamStockRepository.recoverTeamStock(
                releaseResult.getOrderLock().getActivityId(),
                releaseResult.getOrderLock().getTeamId(),
                releaseResult.getOrderLock().getOrderId(),
                releaseResult.getTeam().getValidEndTime());
    }

    private TradeOrderEntity queryTradeOrder(String orderId) {
        return tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
    }

    private PayOrderEntity queryPayOrder(String orderId) {
        return tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
    }

    private GroupBuyCompensationResponse toResponse(TradeOrderEntity tradeOrder,
                                                    PayOrderEntity payOrder,
                                                    RefundOrderEntity refundOrder,
                                                   GroupBuySettlementResult releaseResult,
                                                   LocalDateTime finishTime) {
        GroupBuyCompensationResponse response = new GroupBuyCompensationResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setRefundId(refundOrder == null ? null : refundOrder.getRefundId());
        response.setActivityId(releaseResult.getOrderLock().getActivityId());
        response.setTeamId(releaseResult.getTeam().getTeamId());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setLockStatus(releaseResult.getOrderLock().getLockStatus().name());
        response.setTeamStatus(releaseResult.getTeam().getTeamStatus().name());
        response.setLockedCount(releaseResult.getTeam().getLockCount());
        response.setCompleteCount(releaseResult.getTeam().getCompleteCount());
        response.setRefundAmount(refundOrder == null ? null : refundOrder.getRefundAmount());
        response.setFinishTime(finishTime);
        return response;
    }
}
