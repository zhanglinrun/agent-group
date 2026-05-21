package com.linrun.trigger.service;

import com.linrun.domain.marketing.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.marketing.adapter.GroupBuyStockRepository;
import com.linrun.domain.marketing.model.GroupBuyLockStatus;
import com.linrun.domain.marketing.model.GroupBuySettlementResult;
import com.linrun.domain.marketing.model.GroupBuyTeamStatus;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupBuySettlementService {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final NotifyTaskService notifyTaskService;

    public GroupBuySettlementService(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                     TradeOrderRepository tradeOrderRepository,
                                     TradeStatusFlowService tradeStatusFlowService) {
        this(groupBuyOrderLockRepository, GroupBuyStockRepository.noop(), tradeOrderRepository, tradeStatusFlowService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GroupBuySettlementService(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
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

    public GroupBuySettlementService(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                     GroupBuyStockRepository groupBuyStockRepository,
                                     TradeOrderRepository tradeOrderRepository,
                                     TradeStatusFlowService tradeStatusFlowService) {
        this(groupBuyOrderLockRepository, groupBuyStockRepository, tradeOrderRepository, tradeStatusFlowService, null);
    }

    public void settlePaySuccess(TradeOrderEntity tradeOrder) {
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return;
        }

        GroupBuySettlementResult settlementResult = groupBuyOrderLockRepository.settlePaidOrder(tradeOrder.getOrderId());
        if (settlementResult.isRepeated()) {
            return;
        }
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
        if (!GroupBuyTeamStatus.SUCCESS.equals(settlementResult.getTeam().getTeamStatus())) {
            return;
        }

        List<String> orderIds = groupBuyOrderLockRepository.queryPaidOrderIdsByTeamId(settlementResult.getTeam().getTeamId());
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
    }
}
