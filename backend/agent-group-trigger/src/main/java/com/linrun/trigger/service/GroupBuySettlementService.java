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
import com.linrun.trigger.service.groupbuy.settlement.GroupBuySettlementRuleChain;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupBuySettlementService {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final NotifyTaskService notifyTaskService;
    private final GroupBuySettlementRuleChain settlementRuleChain;

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
        this.settlementRuleChain = new GroupBuySettlementRuleChain(
                groupBuyOrderLockRepository,
                groupBuyStockRepository,
                tradeOrderRepository,
                tradeStatusFlowService,
                notifyTaskService);
    }

    public GroupBuySettlementService(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                     GroupBuyStockRepository groupBuyStockRepository,
                                     TradeOrderRepository tradeOrderRepository,
                                     TradeStatusFlowService tradeStatusFlowService) {
        this(groupBuyOrderLockRepository, groupBuyStockRepository, tradeOrderRepository, tradeStatusFlowService, null);
    }

    public void settlePaySuccess(TradeOrderEntity tradeOrder) {
        settlementRuleChain.settlePaySuccess(tradeOrder);
    }
}
