package com.linrun.domain.groupbuy.service.rules.settlement;

import cn.bugstack.wrench.design.framework.link.model2.LinkArmory;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyLockStatus;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.types.exception.AppException;

import java.util.List;

public class GroupBuySettlementRuleChain {

    private final BusinessLinkedList<GroupBuySettlementContext, DynamicContext, GroupBuySettlementContext> ruleFilter;

    public GroupBuySettlementRuleChain(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       GroupBuyStockRepository groupBuyStockRepository,
                                       TradeOrderRepository tradeOrderRepository,
                                       TradeStatusFlowService tradeStatusFlowService,
                                       NotifyTaskService notifyTaskService) {
        LinkArmory<GroupBuySettlementContext, DynamicContext, GroupBuySettlementContext> linkArmory =
                new LinkArmory<>("group buy settlement rule chain",
                        new TradeTypeRule(),
                        new PaidLockSettlementRule(groupBuyOrderLockRepository, groupBuyStockRepository, tradeStatusFlowService),
                        new TeamSuccessSettlementRule(groupBuyOrderLockRepository, tradeOrderRepository,
                                tradeStatusFlowService, notifyTaskService));
        this.ruleFilter = linkArmory.getLogicLink();
    }

    public List<String> settlePaySuccess(TradeOrderEntity tradeOrder) {
        GroupBuySettlementContext context = new GroupBuySettlementContext(tradeOrder);
        try {
            GroupBuySettlementContext result = ruleFilter.apply(context, new DynamicContext());
            return result == null ? List.of() : result.getSettledOrderIds();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("GROUP_0022", "group buy settlement rule chain failed");
        }
    }

    public static class DynamicContext {
    }

    private static class TradeTypeRule implements ILogicHandler<GroupBuySettlementContext, DynamicContext, GroupBuySettlementContext> {

        @Override
        public GroupBuySettlementContext apply(GroupBuySettlementContext context, DynamicContext dynamicContext) throws Exception {
            if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(context.getTradeOrder().getBuyType())) {
                context.stop();
                return context;
            }
            return next(context, dynamicContext);
        }
    }

    private static class PaidLockSettlementRule implements ILogicHandler<GroupBuySettlementContext, DynamicContext, GroupBuySettlementContext> {

        private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
        private final GroupBuyStockRepository groupBuyStockRepository;
        private final TradeStatusFlowService tradeStatusFlowService;

        private PaidLockSettlementRule(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                       GroupBuyStockRepository groupBuyStockRepository,
                                       TradeStatusFlowService tradeStatusFlowService) {
            this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
            this.groupBuyStockRepository = groupBuyStockRepository;
            this.tradeStatusFlowService = tradeStatusFlowService;
        }

        @Override
        public GroupBuySettlementContext apply(GroupBuySettlementContext context, DynamicContext dynamicContext) throws Exception {
            TradeOrderEntity tradeOrder = context.getTradeOrder();
            GroupBuySettlementResult settlementResult = groupBuyOrderLockRepository.settlePaidOrder(tradeOrder.getOrderId());
            context.setSettlementResult(settlementResult);
            if (settlementResult.isRepeated()) {
                context.stop();
                return context;
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
            return next(context, dynamicContext);
        }
    }

    private static class TeamSuccessSettlementRule implements ILogicHandler<GroupBuySettlementContext, DynamicContext, GroupBuySettlementContext> {

        private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
        private final TradeOrderRepository tradeOrderRepository;
        private final TradeStatusFlowService tradeStatusFlowService;
        private final NotifyTaskService notifyTaskService;

        private TeamSuccessSettlementRule(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                          TradeOrderRepository tradeOrderRepository,
                                          TradeStatusFlowService tradeStatusFlowService,
                                          NotifyTaskService notifyTaskService) {
            this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
            this.tradeOrderRepository = tradeOrderRepository;
            this.tradeStatusFlowService = tradeStatusFlowService;
            this.notifyTaskService = notifyTaskService;
        }

        @Override
        public GroupBuySettlementContext apply(GroupBuySettlementContext context, DynamicContext dynamicContext) {
            GroupBuySettlementResult settlementResult = context.getSettlementResult();
            if (!GroupBuyTeamStatus.SUCCESS.equals(settlementResult.getTeam().getTeamStatus())) {
                context.stop();
                return context;
            }

            List<String> orderIds = groupBuyOrderLockRepository.queryPaidOrderIdsByTeamId(
                    settlementResult.getTeam().getTeamId());
            context.setSettledOrderIds(orderIds);
            tradeOrderRepository.updateGroupSettledByOrderIds(orderIds);
            tradeStatusFlowService.record(
                    context.getTradeOrder().getOrderId(),
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
            if (orderIds.contains(context.getTradeOrder().getOrderId())) {
                context.getTradeOrder().markGroupSettled();
            }
            return context;
        }
    }
}
