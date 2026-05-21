package com.linrun.trigger.service;

import com.linrun.api.order.request.CreateDirectOrderRequest;
import com.linrun.api.order.response.CreateDirectOrderResponse;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.adapter.TradeStatusFlowRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.entity.TradeStatusFlowEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectBuyOrderServiceTest {

    @Test
    void shouldCreateDirectOrderAndPersist() {
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        FakeTradeStatusFlowRepository flowRepository = new FakeTradeStatusFlowRepository();
        DirectBuyOrderService service = new DirectBuyOrderService(
                new FakeGuideDataRepository(),
                tradeOrderRepository,
                new TradeOrderService(),
                new TradeStatusFlowService(flowRepository));
        CreateDirectOrderRequest request = new CreateDirectOrderRequest();
        request.setUserId("U10001");
        request.setGoodsId("G10001");

        CreateDirectOrderResponse response = service.createDirectOrder(request);

        assertTrue(response.getOrderId().startsWith("O"));
        assertTrue(response.getPayOrderId().startsWith("P"));
        assertEquals("U10001", response.getUserId());
        assertEquals("G10001", response.getGoodsId());
        assertEquals("轻薄学习平板标准版", response.getGoodsName());
        assertEquals(TradeBuyTypeEnumVO.DIRECT.name(), response.getBuyType());
        assertEquals(TradeOrderStatusEnumVO.PAY_WAIT.name(), response.getOrderStatus());
        assertEquals(PayStatusEnumVO.WAIT_PAY.name(), response.getPayStatus());
        assertEquals(new BigDecimal("2399.00"), response.getOriginAmount());
        assertEquals(new BigDecimal("2399.00"), response.getPayAmount());
        assertTrue(response.getPayUrl().contains(response.getOrderId()));
        assertNotNull(response.getCreateTime());

        assertEquals(response.getOrderId(), tradeOrderRepository.savedTradeOrder.getOrderId());
        assertEquals(response.getPayOrderId(), tradeOrderRepository.savedPayOrder.getPayOrderId());
        assertEquals(2, flowRepository.flows.size());
        assertEquals(TradeStatusFlowService.EVENT_CREATE_DIRECT_ORDER, flowRepository.flows.get(0).getEventType());
        assertEquals(TradeStatusFlowService.EVENT_CREATE_PAY_ORDER, flowRepository.flows.get(1).getEventType());
    }

    @Test
    void shouldUseRequestPayChannelWhenProvided() {
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        DirectBuyOrderService service = new DirectBuyOrderService(
                new FakeGuideDataRepository(),
                tradeOrderRepository,
                new TradeOrderService(),
                new TradeStatusFlowService(new FakeTradeStatusFlowRepository()));
        CreateDirectOrderRequest request = new CreateDirectOrderRequest();
        request.setUserId("U10001");
        request.setGoodsId("G10001");
        request.setPayChannel("BALANCE_PAY");

        service.createDirectOrder(request);

        assertEquals("BALANCE_PAY", tradeOrderRepository.savedPayOrder.getPayChannel());
    }

    @Test
    void shouldThrowWhenProductMissing() {
        DirectBuyOrderService service = new DirectBuyOrderService(
                new EmptyGuideDataRepository(),
                new FakeTradeOrderRepository(),
                new TradeOrderService(),
                new TradeStatusFlowService(new FakeTradeStatusFlowRepository()));
        CreateDirectOrderRequest request = new CreateDirectOrderRequest();
        request.setUserId("U10001");
        request.setGoodsId("G10099");

        AppException exception = assertThrows(AppException.class, () -> service.createDirectOrder(request));

        assertEquals("DATA_0003", exception.getCode());
        assertEquals("商品不存在或已下架", exception.getMessage());
    }

    @Test
    void shouldThrowWhenUserIdIsBlank() {
        DirectBuyOrderService service = new DirectBuyOrderService(
                new FakeGuideDataRepository(),
                new FakeTradeOrderRepository(),
                new TradeOrderService(),
                new TradeStatusFlowService(new FakeTradeStatusFlowRepository()));
        CreateDirectOrderRequest request = new CreateDirectOrderRequest();
        request.setGoodsId("G10001");

        AppException exception = assertThrows(AppException.class, () -> service.createDirectOrder(request));

        assertEquals("0001", exception.getCode());
        assertEquals("用户编号不能为空", exception.getMessage());
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return queryProductByGoodsId("G10001");
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName("轻薄学习平板标准版");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            return Optional.of(product);
        }
    }

    private static class EmptyGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return Optional.empty();
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return Optional.empty();
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private TradeOrderEntity savedTradeOrder;
        private PayOrderEntity savedPayOrder;

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
            if (savedTradeOrder != null && orderIds.contains(savedTradeOrder.getOrderId())) {
                savedTradeOrder.markGroupSettled();
            }
        }

        @Override
        public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
        }

        @Override
        public void saveRefundOrder(RefundOrderEntity refundOrder) {
        }

        @Override
        public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
        }

        @Override
        public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            return Optional.ofNullable(savedTradeOrder);
        }

        @Override
        public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
            return Optional.ofNullable(savedPayOrder);
        }
    }

    private static class FakeTradeStatusFlowRepository implements TradeStatusFlowRepository {

        private final List<TradeStatusFlowEntity> flows = new java.util.ArrayList<>();

        @Override
        public void save(TradeStatusFlowEntity flow) {
            flows.add(flow);
        }

        @Override
        public List<TradeStatusFlowEntity> queryByOrderId(String orderId) {
            return flows.stream()
                    .filter(flow -> orderId.equals(flow.getOrderId()))
                    .toList();
        }
    }
}
