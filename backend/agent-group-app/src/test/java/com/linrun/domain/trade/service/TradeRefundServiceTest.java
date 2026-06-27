package com.linrun.domain.trade.service;

import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.QueryPaymentRefundRequest;
import com.linrun.api.dto.QueryPaymentRefundResponse;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.groupbuy.service.GroupBuyCompensationService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.payment.PaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRefundServiceTest {

    @Test
    void shouldRollbackQuotaWhenRefundQueryConfirmed() {
        Fixture fixture = fixture();
        QueryPaymentRefundRequest request = new QueryPaymentRefundRequest();
        request.setOrderId("O10001");
        QueryPaymentRefundResponse response = refundResponse(true, "SUCCESS");
        when(fixture.paymentService.queryRefund(request)).thenReturn(response);

        QueryPaymentRefundResponse actual = fixture.service.queryRefund(request);

        assertSame(response, actual);
        verify(fixture.userQuotaService).rollbackQuotaForRefundedOrder(fixture.repository.tradeOrder);
    }

    @Test
    void shouldRollbackQuotaWhenRefundWebhookConfirmed() {
        Fixture fixture = fixture();
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setOrderId("O10001");
        QueryPaymentRefundResponse response = refundResponse(true, "REFUND_SUCCESS");
        when(fixture.paymentService.handleRefundWebhook(request)).thenReturn(response);

        QueryPaymentRefundResponse actual = fixture.service.handleRefundWebhook(request);

        assertSame(response, actual);
        verify(fixture.userQuotaService).rollbackQuotaForRefundedOrder(fixture.repository.tradeOrder);
    }

    @Test
    void shouldNotRollbackQuotaWhenRefundQueryIsNotConfirmed() {
        Fixture fixture = fixture();
        QueryPaymentRefundRequest request = new QueryPaymentRefundRequest();
        request.setOrderId("O10001");
        QueryPaymentRefundResponse response = refundResponse(true, "PROCESSING");
        when(fixture.paymentService.queryRefund(request)).thenReturn(response);

        fixture.service.queryRefund(request);

        verify(fixture.userQuotaService, never()).rollbackQuotaForRefundedOrder(fixture.repository.tradeOrder);
    }

    private static Fixture fixture() {
        FakeTradeOrderRepository repository = new FakeTradeOrderRepository();
        PaymentService paymentService = mock(PaymentService.class);
        GroupBuyCompensationService groupBuyCompensationService = mock(GroupBuyCompensationService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        TradeRefundService service = new TradeRefundService(
                repository,
                paymentService,
                groupBuyCompensationService,
                null,
                null,
                userQuotaService);
        return new Fixture(service, repository, paymentService, userQuotaService);
    }

    private static QueryPaymentRefundResponse refundResponse(boolean verified, String refundStatus) {
        QueryPaymentRefundResponse response = new QueryPaymentRefundResponse();
        response.setOrderId("O10001");
        response.setPayOrderId("P10001");
        response.setRefundId("RO10001");
        response.setRefundStatus(refundStatus);
        response.setVerified(verified);
        response.setRefundAmount(new BigDecimal("2399.00"));
        response.setRefundTime(LocalDateTime.of(2026, 5, 14, 10, 0));
        return response;
    }

    private record Fixture(TradeRefundService service,
                           FakeTradeOrderRepository repository,
                           PaymentService paymentService,
                           UserQuotaService userQuotaService) {
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private final TradeOrderEntity tradeOrder;

        private FakeTradeOrderRepository() {
            tradeOrder = new TradeOrderEntity();
            tradeOrder.setOrderId("O10001");
            tradeOrder.setUserId("U10001");
            tradeOrder.setGoodsId("G10001");
            tradeOrder.setGoodsName("基础 Agent 额度包");
            tradeOrder.setBuyType(TradeBuyTypeEnumVO.DIRECT);
            tradeOrder.setOriginAmount(new BigDecimal("2399.00"));
            tradeOrder.setPayAmount(new BigDecimal("2399.00"));
            tradeOrder.setOrderStatus(TradeOrderStatusEnumVO.REFUNDED);
            tradeOrder.setCreateTime(LocalDateTime.now());
        }

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
        }

        @Override
        public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void saveRefundOrder(RefundOrderEntity refundOrder) {
        }

        @Override
        public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            return Optional.of(tradeOrder);
        }

        @Override
        public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
            return Optional.empty();
        }
    }
}
