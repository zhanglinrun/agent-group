package com.linrun.infrastructure.adapter.port;

import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.model.GroupBuyLockStatus;
import com.linrun.domain.activity.model.GroupBuyOrderLock;
import com.linrun.domain.activity.model.GroupBuyTeam;
import com.linrun.domain.activity.model.GroupBuyTeamStatus;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeAuditPortAdapterTest {

    @Test
    void shouldMarkPaidGroupOrderAsWaitingSettlementWithoutQuotaGrant() {
        TradeOrderRepository tradeOrderRepository = mock(TradeOrderRepository.class);
        GroupBuyOrderLockRepository groupBuyOrderLockRepository = mock(GroupBuyOrderLockRepository.class);
        UserQuotaRepository userQuotaRepository = mock(UserQuotaRepository.class);
        TradeStatusFlowRepository tradeStatusFlowRepository = mock(TradeStatusFlowRepository.class);
        TradeAuditPortAdapter adapter = new TradeAuditPortAdapter(
                tradeOrderRepository, groupBuyOrderLockRepository, userQuotaRepository, tradeStatusFlowRepository);

        TradeOrderEntity order = groupOrder();
        PayOrderEntity payOrder = payOrder();
        GroupBuyOrderLock lock = lock();
        GroupBuyTeam team = team();
        UserQuotaAccount account = account();

        when(tradeOrderRepository.queryTradeOrderByOrderId("O1001")).thenReturn(Optional.of(order));
        when(tradeOrderRepository.queryPayOrderByOrderId("O1001")).thenReturn(Optional.of(payOrder));
        when(tradeOrderRepository.queryRefundOrderByOrderId("O1001")).thenReturn(Optional.empty());
        when(groupBuyOrderLockRepository.queryLockByOrderId("O1001")).thenReturn(Optional.of(lock));
        when(groupBuyOrderLockRepository.queryTeamByTeamId("T1001")).thenReturn(Optional.of(team));
        when(userQuotaRepository.queryAccount("U1001")).thenReturn(Optional.of(account));
        when(userQuotaRepository.queryFlow("U1001", "ORDER_GRANT", "O1001")).thenReturn(Optional.empty());
        when(userQuotaRepository.queryFlow("U1001", "REFUND_ROLLBACK", "O1001")).thenReturn(Optional.empty());
        when(tradeStatusFlowRepository.queryByOrderId("O1001")).thenReturn(List.of());

        AcademicTradeAuditPort.AcademicTradeAuditResult result = adapter.audit(
                new AcademicTradeAuditPort.AcademicTradeAuditRequest(
                        "U1001", "O1001", "", "", 8, 20, false));

        assertTrue(result.success());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "PAID_WAITING_GROUP_SETTLEMENT".equals(finding.get("code"))));
        Map<?, ?> flags = (Map<?, ?>) result.snapshot().get("auditFlags");
        assertEquals(false, flags.get("quotaGrantable"));
        assertEquals(false, flags.get("quotaGranted"));
    }

    private TradeOrderEntity groupOrder() {
        TradeOrderEntity order = new TradeOrderEntity();
        order.setId(1L);
        order.setOrderId("O1001");
        order.setUserId("U1001");
        order.setGoodsId("G1001");
        order.setGoodsName("quota pack");
        order.setActivityId("A1001");
        order.setBuyType(TradeBuyTypeEnumVO.GROUP_BUY);
        order.setOriginAmount(BigDecimal.valueOf(99));
        order.setPayAmount(BigDecimal.valueOf(59));
        order.setOrderStatus(TradeOrderStatusEnumVO.PAY_SUCCESS);
        order.setCreateTime(LocalDateTime.now());
        order.setPayTime(LocalDateTime.now());
        return order;
    }

    private PayOrderEntity payOrder() {
        PayOrderEntity payOrder = new PayOrderEntity();
        payOrder.setPayOrderId("P1001");
        payOrder.setOrderId("O1001");
        payOrder.setPayChannel("mock");
        payOrder.setPayAmount(BigDecimal.valueOf(59));
        payOrder.setPayStatus(PayStatusEnumVO.SUCCESS);
        payOrder.setCreateTime(LocalDateTime.now());
        payOrder.setPayTime(LocalDateTime.now());
        return payOrder;
    }

    private GroupBuyOrderLock lock() {
        GroupBuyOrderLock lock = new GroupBuyOrderLock();
        lock.setLockId("L1001");
        lock.setUserId("U1001");
        lock.setTeamId("T1001");
        lock.setOrderId("O1001");
        lock.setActivityId("A1001");
        lock.setGoodsId("G1001");
        lock.setLockAmount(BigDecimal.valueOf(59));
        lock.setLockStatus(GroupBuyLockStatus.PAID);
        lock.setLockTime(LocalDateTime.now());
        return lock;
    }

    private GroupBuyTeam team() {
        GroupBuyTeam team = new GroupBuyTeam();
        team.setTeamId("T1001");
        team.setActivityId("A1001");
        team.setGoodsId("G1001");
        team.setTargetCount(3);
        team.setCompleteCount(1);
        team.setLockCount(1);
        team.setTeamStatus(GroupBuyTeamStatus.PROCESSING);
        team.setValidStartTime(LocalDateTime.now().minusMinutes(10));
        team.setValidEndTime(LocalDateTime.now().plusMinutes(20));
        team.setCreateTime(LocalDateTime.now().minusMinutes(10));
        return team;
    }

    private UserQuotaAccount account() {
        UserQuotaAccount account = new UserQuotaAccount();
        account.setUserId("U1001");
        account.setQuotaBalance(BigDecimal.TEN);
        return account;
    }
}
