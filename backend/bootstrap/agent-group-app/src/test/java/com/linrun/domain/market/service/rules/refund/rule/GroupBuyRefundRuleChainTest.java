package com.linrun.domain.market.service.rules.refund.rule;

import com.linrun.api.dto.GroupBuyCompensationResponse;
import com.linrun.api.dto.RefundGroupBuyOrderRequest;
import com.linrun.domain.market.service.GroupBuyCompensationService;
import com.linrun.domain.market.service.rules.refund.GroupBuyRefundStrategyRouter;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GroupBuyRefundRuleChainTest {

    @Test
    void shouldCreateRefundNotifyTaskWhenRefundOrderAlreadyExists() {
        TradeOrderRepository tradeOrderRepository = mock(TradeOrderRepository.class);
        GroupBuyCompensationService groupBuyCompensationService = mock(GroupBuyCompensationService.class);
        GroupBuyRefundStrategyRouter refundStrategyRouter = mock(GroupBuyRefundStrategyRouter.class);
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        GroupBuyRefundRuleChain chain = new GroupBuyRefundRuleChain(
                tradeOrderRepository,
                groupBuyCompensationService,
                refundStrategyRouter,
                notifyTaskService);
        RefundGroupBuyOrderRequest request = new RefundGroupBuyOrderRequest();
        request.setOrderId("O10001");
        TradeOrderEntity tradeOrder = new TradeOrderEntity();
        tradeOrder.setOrderId("O10001");
        tradeOrder.setBuyType(TradeBuyTypeEnumVO.GROUP_BUY);
        PayOrderEntity payOrder = new PayOrderEntity();
        RefundOrderEntity refundOrder = new RefundOrderEntity();
        GroupBuyCompensationResponse response = new GroupBuyCompensationResponse();
        response.setOrderId("O10001");
        response.setRefundId("R10001");
        when(tradeOrderRepository.queryTradeOrderByOrderId("O10001")).thenReturn(Optional.of(tradeOrder));
        when(tradeOrderRepository.queryPayOrderByOrderId("O10001")).thenReturn(Optional.of(payOrder));
        when(tradeOrderRepository.queryRefundOrderByOrderId("O10001")).thenReturn(Optional.of(refundOrder));
        when(groupBuyCompensationService.releaseRefundedOrder(request)).thenReturn(response);

        GroupBuyCompensationResponse actual = chain.refund(request);

        assertSame(response, actual);
        verify(groupBuyCompensationService).releaseRefundedOrder(request);
        verify(notifyTaskService).createGroupRefundTask(response);
        verifyNoInteractions(refundStrategyRouter);
    }
}
