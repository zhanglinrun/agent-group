package com.linrun.trigger.service;

import com.linrun.api.order.request.CreateDirectOrderRequest;
import com.linrun.api.order.response.CreateDirectOrderResponse;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.CreateTradeOrderCommand;
import com.linrun.domain.order.model.PayOrder;
import com.linrun.domain.order.model.TradeBuyType;
import com.linrun.domain.order.model.TradeOrder;
import com.linrun.domain.order.model.TradePayOrder;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DirectBuyOrderService {

    private static final String DEFAULT_PAY_CHANNEL = "MOCK_PAY";

    private final GuideDataRepository guideDataRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;

    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService) {
        this.guideDataRepository = guideDataRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
    }

    public CreateDirectOrderResponse createDirectOrder(CreateDirectOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "下单参数不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "商品编号不能为空");
        }

        GuideProduct product = guideDataRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "商品不存在或已下架"));

        CreateTradeOrderCommand command = new CreateTradeOrderCommand();
        command.setUserId(request.getUserId());
        command.setGoodsId(product.getGoodsId());
        command.setGoodsName(product.getGoodsName());
        command.setBuyType(TradeBuyType.DIRECT);
        command.setOriginAmount(product.getOriginPrice());
        command.setPayAmount(product.getOriginPrice());

        TradeOrder tradeOrder = tradeOrderService.createOrder(command);
        TradePayOrder tradePayOrder = tradeOrderService.createPayOrder(tradeOrder, resolvePayChannel(request));
        tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
        recordCreateFlow(tradePayOrder);

        return toResponse(tradePayOrder);
    }

    private void recordCreateFlow(TradePayOrder tradePayOrder) {
        TradeOrder tradeOrder = tradePayOrder.getTradeOrder();
        PayOrder payOrder = tradePayOrder.getPayOrder();
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_CREATE_DIRECT_ORDER,
                null,
                tradeOrder.getOrderStatus(),
                "direct order created");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_CREATE_PAY_ORDER,
                null,
                payOrder.getPayStatus(),
                "pay order created");
    }

    private String resolvePayChannel(CreateDirectOrderRequest request) {
        return StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel() : DEFAULT_PAY_CHANNEL;
    }

    private CreateDirectOrderResponse toResponse(TradePayOrder tradePayOrder) {
        TradeOrder tradeOrder = tradePayOrder.getTradeOrder();
        PayOrder payOrder = tradePayOrder.getPayOrder();

        CreateDirectOrderResponse response = new CreateDirectOrderResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setUserId(tradeOrder.getUserId());
        response.setGoodsId(tradeOrder.getGoodsId());
        response.setGoodsName(tradeOrder.getGoodsName());
        response.setBuyType(tradeOrder.getBuyType().name());
        response.setOrderStatus(tradeOrder.getOrderStatus().name());
        response.setPayStatus(payOrder.getPayStatus().name());
        response.setOriginAmount(tradeOrder.getOriginAmount());
        response.setPayAmount(payOrder.getPayAmount());
        response.setPayUrl(payOrder.getPayUrl());
        response.setCreateTime(tradeOrder.getCreateTime());
        return response;
    }
}
