package com.linrun.domain.market.service;

import com.linrun.domain.market.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyLockStatus;
import com.linrun.domain.market.model.GroupBuySettlementResult;
import com.linrun.domain.market.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.market.service.rules.settlement.GroupBuySettlementRuleChain;
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

    public List<String> settlePaySuccess(TradeOrderEntity tradeOrder) {
        return settlementRuleChain.settlePaySuccess(tradeOrder);
    }
}















