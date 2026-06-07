package com.linrun.domain.trade.service;

import com.linrun.api.dto.CreateDirectOrderRequest;
import com.linrun.api.dto.CreateDirectOrderResponse;
import com.linrun.domain.agent.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.service.GuideDecisionSnapshotValidator;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.CreateTradeOrderCommandEntity;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.aggregate.TradePayOrderAggregate;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class DirectBuyOrderService {

    private static final String DEFAULT_PAY_CHANNEL = "ALIPAY";

    private final GuideDataRepository guideDataRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final GuideDecisionSnapshotValidator guideDecisionSnapshotValidator;

    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                new GuideDecisionSnapshotValidator(GuideDecisionSnapshotRepository.noop()));
    }

    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService,
                                 GuideDecisionSnapshotRepository guideDecisionSnapshotRepository) {
        this(guideDataRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                new GuideDecisionSnapshotValidator(guideDecisionSnapshotRepository));
    }

    @Autowired
    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService,
                                 GuideDecisionSnapshotValidator guideDecisionSnapshotValidator) {
        this.guideDataRepository = guideDataRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.guideDecisionSnapshotValidator = guideDecisionSnapshotValidator;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateDirectOrderResponse createDirectOrder(CreateDirectOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "下单参数不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "额度包编号不能为空");
        }
        if (!StringUtils.hasText(request.getIdempotentKey())) {
            throw new AppException("0001", "幂等键不能为空");
        }
        TradeOrderEntity existed = tradeOrderRepository.queryTradeOrderByIdempotentKey(request.getIdempotentKey())
                .orElse(null);
        if (existed != null) {
            validateExistingOrder(existed, request);
            PayOrderEntity existingPayOrder = tradeOrderRepository.queryPayOrderByOrderId(existed.getOrderId())
                    .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));
            return toResponse(existed, existingPayOrder, request.getDecisionId());
        }

        GuideProduct product = guideDataRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "额度包不存在或已下架"));
        if (StringUtils.hasText(request.getDecisionId())) {
            guideDecisionSnapshotValidator.validateDirect(
                    request.getDecisionId(),
                    request.getUserId(),
                    request.getGoodsId(),
                    product.getOriginPrice(),
                    LocalDateTime.now());
        }

        CreateTradeOrderCommandEntity command = new CreateTradeOrderCommandEntity();
        command.setUserId(request.getUserId());
        command.setGoodsId(product.getGoodsId());
        command.setGoodsName(product.getGoodsName());
        command.setIdempotentKey(request.getIdempotentKey());
        command.setBuyType(TradeBuyTypeEnumVO.DIRECT);
        command.setOriginAmount(product.getOriginPrice());
        command.setPayAmount(product.getOriginPrice());

        TradeOrderEntity tradeOrder = tradeOrderService.createOrder(command);
        TradePayOrderAggregate tradePayOrder = tradeOrderService.createPayOrder(tradeOrder, resolvePayChannel(request));
        tradeOrderRepository.save(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder());
        recordCreateFlow(tradePayOrder);

        return toResponse(tradePayOrder, request.getDecisionId());
    }

    private void validateExistingOrder(TradeOrderEntity existed, CreateDirectOrderRequest request) {
        if (!request.getUserId().equals(existed.getUserId())
                || !request.getGoodsId().equals(existed.getGoodsId())
                || !TradeBuyTypeEnumVO.DIRECT.equals(existed.getBuyType())) {
            throw new AppException("TRADE_0017", "幂等键已被其他下单请求使用");
        }
    }

    private void recordCreateFlow(TradePayOrderAggregate tradePayOrder) {
        TradeOrderEntity tradeOrder = tradePayOrder.getTradeOrder();
        PayOrderEntity payOrder = tradePayOrder.getPayOrder();
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

    private CreateDirectOrderResponse toResponse(TradePayOrderAggregate tradePayOrder, String decisionId) {
        return toResponse(tradePayOrder.getTradeOrder(), tradePayOrder.getPayOrder(), decisionId);
    }

    private CreateDirectOrderResponse toResponse(TradeOrderEntity tradeOrder, PayOrderEntity payOrder, String decisionId) {
        CreateDirectOrderResponse response = new CreateDirectOrderResponse();
        response.setOrderId(tradeOrder.getOrderId());
        response.setPayOrderId(payOrder.getPayOrderId());
        response.setIdempotentKey(tradeOrder.getIdempotentKey());
        response.setDecisionId(decisionId);
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
