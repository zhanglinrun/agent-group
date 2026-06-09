package com.linrun.trigger.http.trade;

import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.DirectBuyOrderService;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradeOrderControllerTest {

    @Test
    void shouldExposeReadableDisplayStatusForDirectAndGroupOrders() throws Exception {
        TradeOrderRepository tradeOrderRepository = mock(TradeOrderRepository.class);
        RefundOrderEntity timeoutRefund = new RefundOrderEntity();
        timeoutRefund.setRefundReason("拼团未成团超时退�?);

        when(tradeOrderRepository.queryTradeOrders(isNull(), eq(21), isNull(), isNull(), isNull()))
                .thenReturn(List.of(
                        order(1L, "O-DIRECT-PAID", TradeBuyTypeEnumVO.DIRECT, TradeOrderStatusEnumVO.PAY_SUCCESS),
                        order(2L, "O-GROUP-WAIT", TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.PAY_SUCCESS),
                        order(3L, "O-GROUP-SETTLED", TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.GROUP_SETTLED),
                        order(4L, "O-GROUP-REFUNDED", TradeBuyTypeEnumVO.GROUP_BUY, TradeOrderStatusEnumVO.REFUNDED)));
        when(tradeOrderRepository.queryPayOrderByOrderId(anyString())).thenReturn(Optional.empty());
        when(tradeOrderRepository.queryRefundOrderByOrderId(anyString())).thenReturn(Optional.empty());
        when(tradeOrderRepository.queryRefundOrderByOrderId("O-GROUP-REFUNDED")).thenReturn(Optional.of(timeoutRefund));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TradeOrderController(
                        mock(DirectBuyOrderService.class),
                        mock(TradeConsistencyCheckService.class),
                        mock(TradeStatusFlowService.class),
                        mock(UserAccountService.class),
                        tradeOrderRepository))
                .build();

        mockMvc.perform(post("/api/v1/trade/order/admin")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderList[0].displayStatus").value("已支付，额度已到�?))
                .andExpect(jsonPath("$.data.orderList[1].displayStatus").value("支付成功，等待成�?))
                .andExpect(jsonPath("$.data.orderList[2].displayStatus").value("拼团已成团，额度已到�?))
                .andExpect(jsonPath("$.data.orderList[3].displayStatus").value("拼团未成团，已退�?));
    }

    private TradeOrderEntity order(Long id,
                                   String orderId,
                                   TradeBuyTypeEnumVO buyType,
                                   TradeOrderStatusEnumVO orderStatus) {
        TradeOrderEntity order = new TradeOrderEntity();
        order.setId(id);
        order.setOrderId(orderId);
        order.setUserId("U1001");
        order.setGoodsId("G1001");
        order.setGoodsName("基础额度�?);
        order.setBuyType(buyType);
        order.setOriginAmount(new BigDecimal("19.90"));
        order.setPayAmount(new BigDecimal("16.90"));
        order.setOrderStatus(orderStatus);
        order.setCreateTime(LocalDateTime.of(2026, 6, 8, 10, 0));
        return order;
    }
}















