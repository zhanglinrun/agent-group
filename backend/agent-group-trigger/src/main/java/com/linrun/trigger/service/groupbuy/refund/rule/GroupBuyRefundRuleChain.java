package com.linrun.trigger.service.groupbuy.refund.rule;

import cn.bugstack.wrench.design.framework.link.model2.LinkArmory;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.api.marketing.request.RefundGroupBuyOrderRequest;
import com.linrun.api.marketing.response.GroupBuyCompensationResponse;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.trigger.service.GroupBuyCompensationService;
import com.linrun.trigger.service.NotifyTaskService;
import com.linrun.trigger.service.groupbuy.refund.GroupBuyRefundStrategyRouter;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

public class GroupBuyRefundRuleChain {

    private final BusinessLinkedList<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> ruleFilter;

    public GroupBuyRefundRuleChain(TradeOrderRepository tradeOrderRepository,
                                   GroupBuyCompensationService groupBuyCompensationService,
                                   GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter,
                                   NotifyTaskService notifyTaskService) {
        LinkArmory<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> linkArmory =
                new LinkArmory<>("group buy refund rule chain",
                        new RequestValidateRule(),
                        new DataLoadRule(tradeOrderRepository),
                        new GroupBuyOrderRule(),
                        new UniqueRefundRule(tradeOrderRepository, groupBuyCompensationService),
                        new RefundStrategyRule(groupBuyRefundStrategyRouter),
                        new RefundNotifyRule(notifyTaskService));
        this.ruleFilter = linkArmory.getLogicLink();
    }

    public GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request) {
        GroupBuyRefundContext context = new GroupBuyRefundContext(request);
        try {
            return ruleFilter.apply(context, new DynamicContext()).getResponse();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("TRADE_0021", "group buy refund rule chain failed");
        }
    }

    public static class DynamicContext {
    }

    private static class RequestValidateRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) throws Exception {
            if (context.getRequest() == null || !StringUtils.hasText(context.getRequest().getOrderId())) {
                throw new AppException("0001", "orderId cannot be blank");
            }
            return next(context, dynamicContext);
        }
    }

    private static class DataLoadRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        private final TradeOrderRepository tradeOrderRepository;

        private DataLoadRule(TradeOrderRepository tradeOrderRepository) {
            this.tradeOrderRepository = tradeOrderRepository;
        }

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) throws Exception {
            String orderId = context.getRequest().getOrderId();
            TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                    .orElseThrow(() -> new AppException("TRADE_0013", "order not found"));
            PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId)
                    .orElseThrow(() -> new AppException("TRADE_0014", "pay order not found"));
            context.setTradeOrder(tradeOrder);
            context.setPayOrder(payOrder);
            return next(context, dynamicContext);
        }
    }

    private static class GroupBuyOrderRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) throws Exception {
            if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(context.getTradeOrder().getBuyType())) {
                throw new AppException("TRADE_0008", "non group buy order cannot use group refund");
            }
            return next(context, dynamicContext);
        }
    }

    private static class UniqueRefundRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        private final TradeOrderRepository tradeOrderRepository;
        private final GroupBuyCompensationService groupBuyCompensationService;

        private UniqueRefundRule(TradeOrderRepository tradeOrderRepository,
                                 GroupBuyCompensationService groupBuyCompensationService) {
            this.tradeOrderRepository = tradeOrderRepository;
            this.groupBuyCompensationService = groupBuyCompensationService;
        }

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) throws Exception {
            RefundOrderEntity existed = tradeOrderRepository
                    .queryRefundOrderByOrderId(context.getRequest().getOrderId())
                    .orElse(null);
            if (existed != null) {
                context.setResponse(groupBuyCompensationService.releaseRefundedOrder(context.getRequest()));
                return context;
            }
            return next(context, dynamicContext);
        }
    }

    private static class RefundStrategyRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        private final GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter;

        private RefundStrategyRule(GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter) {
            this.groupBuyRefundStrategyRouter = groupBuyRefundStrategyRouter;
        }

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) throws Exception {
            context.setResponse(groupBuyRefundStrategyRouter.refund(
                    context.getRequest(), context.getTradeOrder(), context.getPayOrder()));
            return next(context, dynamicContext);
        }
    }

    private static class RefundNotifyRule implements ILogicHandler<GroupBuyRefundContext, DynamicContext, GroupBuyRefundContext> {

        private final NotifyTaskService notifyTaskService;

        private RefundNotifyRule(NotifyTaskService notifyTaskService) {
            this.notifyTaskService = notifyTaskService;
        }

        @Override
        public GroupBuyRefundContext apply(GroupBuyRefundContext context, DynamicContext dynamicContext) {
            if (notifyTaskService != null && context.getResponse() != null) {
                notifyTaskService.createGroupRefundTask(context.getResponse());
            }
            return context;
        }
    }
}
