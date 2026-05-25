package com.linrun.trigger.service;

import com.linrun.api.order.request.CreateDirectOrderRequest;
import com.linrun.api.order.response.CreateDirectOrderResponse;
import com.linrun.domain.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideDecisionSnapshot;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.CreateTradeOrderCommandEntity;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.aggregate.TradePayOrderAggregate;
import com.linrun.domain.order.service.TradeOrderService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class DirectBuyOrderService {

    private static final String DEFAULT_PAY_CHANNEL = "MOCK_PAY";

    private final GuideDataRepository guideDataRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final GuideDecisionSnapshotRepository guideDecisionSnapshotRepository;

    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService) {
        this(guideDataRepository, tradeOrderRepository, tradeOrderService, tradeStatusFlowService,
                GuideDecisionSnapshotRepository.noop());
    }

    @Autowired
    public DirectBuyOrderService(GuideDataRepository guideDataRepository,
                                 TradeOrderRepository tradeOrderRepository,
                                 TradeOrderService tradeOrderService,
                                 TradeStatusFlowService tradeStatusFlowService,
                                 GuideDecisionSnapshotRepository guideDecisionSnapshotRepository) {
        this.guideDataRepository = guideDataRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.guideDecisionSnapshotRepository = guideDecisionSnapshotRepository == null
                ? GuideDecisionSnapshotRepository.noop()
                : guideDecisionSnapshotRepository;
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
            throw new AppException("0001", "商品编号不能为空");
        }
        if (!StringUtils.hasText(request.getIdempotentKey())) {
            throw new AppException("0001", "幂等键不能为空");
        }
        if (!StringUtils.hasText(request.getDecisionId())) {
            throw new AppException("GUIDE_0005", "导购决策编号不能为空，请先完成导购推荐后再下单");
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
                .orElseThrow(() -> new AppException("DATA_0003", "商品不存在或已下架"));
        validateDecisionSnapshot(request, product);

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

    private GuideDecisionSnapshot validateDecisionSnapshot(CreateDirectOrderRequest request, GuideProduct product) {
        GuideDecisionSnapshot snapshot = guideDecisionSnapshotRepository.queryByDecisionId(request.getDecisionId())
                .orElseThrow(() -> new AppException("GUIDE_0006", "导购决策不存在或已过期，请重新发起导购"));
        if (snapshot.isExpired(LocalDateTime.now())) {
            throw new AppException("GUIDE_0008", "导购报价已过期，请重新发起导购");
        }
        if (StringUtils.hasText(snapshot.getUserId()) && !request.getUserId().equals(snapshot.getUserId())) {
            throw new AppException("GUIDE_0007", "导购决策不属于当前用户");
        }
        if (!request.getGoodsId().equals(snapshot.getGoodsId())) {
            throw new AppException("GUIDE_0009", "下单商品和导购决策不一致");
        }
        if (compareAmount(snapshot.getOriginAmount(), product.getOriginPrice()) != 0) {
            throw new AppException("GUIDE_0010", "商品价格已变化，请重新发起导购");
        }
        return snapshot;
    }

    private int compareAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right ? 0 : -1;
        }
        return left.compareTo(right);
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
