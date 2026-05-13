package com.linrun.trigger.service;

import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupBuySettlementService {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final TradeOrderRepository tradeOrderRepository;

    public GroupBuySettlementService(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                     TradeOrderRepository tradeOrderRepository) {
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    public void settlePaySuccess(TradeOrder tradeOrder) {
        if (!TradeBuyType.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return;
        }

        GroupBuySettlementResult settlementResult = groupBuyOrderLockRepository.settlePaidOrder(tradeOrder.getOrderId());
        if (!GroupBuyTeamStatus.SUCCESS.equals(settlementResult.getTeam().getTeamStatus())) {
            return;
        }

        List<String> orderIds = groupBuyOrderLockRepository.queryPaidOrderIdsByTeamId(settlementResult.getTeam().getTeamId());
        tradeOrderRepository.updateGroupSettledByOrderIds(orderIds);
        if (orderIds.contains(tradeOrder.getOrderId())) {
            tradeOrder.markGroupSettled();
        }
    }
}
