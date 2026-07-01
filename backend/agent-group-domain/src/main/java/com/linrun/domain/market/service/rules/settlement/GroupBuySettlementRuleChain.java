package com.linrun.domain.market.service.rules.settlement;

import com.linrun.domain.market.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyLockStatus;
import com.linrun.domain.market.model.GroupBuySettlementResult;
import com.linrun.domain.market.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.domain.trade.service.task.NotifyTaskService;

import java.util.List;

/**
 * 拼团支付成功后的结算流程，按固定顺序执行：
 * 拼团类型过滤 → 名额支付结算（重复结算在此幂等返回） → 成团判定与同队订单批量推进。
 *
 * 返回成团后进入 GROUP_SETTLED 的订单号列表；未成团或重复结算返回空列表。
 */
public class GroupBuySettlementRuleChain {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final NotifyTaskService notifyTaskService;

    public GroupBuySettlementRuleChain(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       GroupBuyStockRepository groupBuyStockRepository,
                                       TradeOrderRepository tradeOrderRepository,
                                       TradeStatusFlowService tradeStatusFlowService,
                                       NotifyTaskService notifyTaskService) {
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.notifyTaskService = notifyTaskService;
    }

    public List<String> settlePaySuccess(TradeOrderEntity tradeOrder) {
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return List.of();
        }
        GroupBuySettlementResult settlementResult = groupBuyOrderLockRepository.settlePaidOrder(tradeOrder.getOrderId());
        if (settlementResult.isRepeated()) {
            return List.of();
        }
        settlePaidLock(tradeOrder, settlementResult);
        if (!GroupBuyTeamStatus.SUCCESS.equals(settlementResult.getTeam().getTeamStatus())) {
            return List.of();
        }
        return settleTeamSuccess(tradeOrder, settlementResult);
    }

    private void settlePaidLock(TradeOrderEntity tradeOrder, GroupBuySettlementResult settlementResult) {
        groupBuyStockRepository.markPaidStock(
                settlementResult.getOrderLock().getActivityId(),
                settlementResult.getOrderLock().getGoodsId(),
                settlementResult.getOrderLock().getOrderId(),
                settlementResult.getOrderLock().getTeamId());
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_GROUP,
                settlementResult.getOrderLock().getLockId(),
                TradeStatusFlowService.EVENT_GROUP_LOCK_PAID,
                GroupBuyLockStatus.LOCKED,
                settlementResult.getOrderLock().getLockStatus(),
                "group lock paid");
    }

    private List<String> settleTeamSuccess(TradeOrderEntity tradeOrder, GroupBuySettlementResult settlementResult) {
        List<String> orderIds = groupBuyOrderLockRepository.queryPaidOrderIdsByTeamId(
                settlementResult.getTeam().getTeamId());
        tradeOrderRepository.updateGroupSettledByOrderIds(orderIds);
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_GROUP,
                settlementResult.getTeam().getTeamId(),
                TradeStatusFlowService.EVENT_GROUP_SETTLED,
                GroupBuyTeamStatus.PROCESSING,
                settlementResult.getTeam().getTeamStatus(),
                "group settled");
        orderIds.forEach(orderId -> tradeStatusFlowService.record(
                orderId,
                TradeStatusFlowService.BIZ_ORDER,
                orderId,
                TradeStatusFlowService.EVENT_GROUP_SETTLED,
                TradeOrderStatusEnumVO.PAY_SUCCESS,
                TradeOrderStatusEnumVO.GROUP_SETTLED,
                "order group settled"));
        if (notifyTaskService != null) {
            notifyTaskService.createGroupSettlementTask(settlementResult.getTeam(), orderIds);
        }
        if (orderIds.contains(tradeOrder.getOrderId())) {
            tradeOrder.markGroupSettled();
        }
        return orderIds;
    }
}
