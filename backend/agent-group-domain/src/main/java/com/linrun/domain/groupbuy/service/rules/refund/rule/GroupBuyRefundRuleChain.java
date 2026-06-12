package com.linrun.domain.groupbuy.service.rules.refund.rule;

import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.groupbuy.service.rules.refund.GroupBuyRefundStrategyRouter;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

/**
 * 拼团退款流程，按固定顺序执行：
 * 参数校验 → 订单加载 → 拼团类型校验 → 退款幂等（已有退款单只补做名额释放） →
 * 按订单状态路由退款策略 → 创建退款通知任务。
 */
public class GroupBuyRefundRuleChain {

    private final TradeOrderRepository tradeOrderRepository;
    private final GroupBuyCompensationService groupBuyCompensationService;
    private final GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter;
    private final NotifyTaskService notifyTaskService;

    public GroupBuyRefundRuleChain(TradeOrderRepository tradeOrderRepository,
                                   GroupBuyCompensationService groupBuyCompensationService,
                                   GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter,
                                   NotifyTaskService notifyTaskService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.groupBuyCompensationService = groupBuyCompensationService;
        this.groupBuyRefundStrategyRouter = groupBuyRefundStrategyRouter;
        this.notifyTaskService = notifyTaskService;
    }

    public GroupBuyCompensationResponse refund(RefundGroupBuyOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        String orderId = request.getOrderId();
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
        if (!TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            throw new AppException("TRADE_0008", "非拼团订单不能发起拼团退款");
        }

        RefundOrderEntity existed = tradeOrderRepository.queryRefundOrderByOrderId(orderId).orElse(null);
        if (existed != null) {
            // 退款单已存在：按幂等结果返回，只补做名额释放，不再重复退款和通知
            return groupBuyCompensationService.releaseRefundedOrder(request);
        }

        GroupBuyCompensationResponse response = groupBuyRefundStrategyRouter.refund(request, tradeOrder, payOrder);
        if (notifyTaskService != null && response != null) {
            notifyTaskService.createGroupRefundTask(response);
        }
        return response;
    }
}
