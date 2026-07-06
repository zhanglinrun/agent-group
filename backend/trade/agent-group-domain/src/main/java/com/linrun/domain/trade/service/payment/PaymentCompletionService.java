package com.linrun.domain.trade.service.payment;

import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.domain.market.service.GroupBuySettlementService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.payment.PaymentCompletionCommand;
import com.linrun.domain.trade.model.payment.PaymentCompletionResult;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.domain.trade.service.TradeOrderService;
import com.linrun.domain.trade.service.TradeStatusFlowService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentCompletionService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderService tradeOrderService;
    private final GroupBuySettlementService groupBuySettlementService;
    private final TradeStatusFlowService tradeStatusFlowService;
    private final UserQuotaService userQuotaService;

    public PaymentCompletionService(TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    GroupBuySettlementService groupBuySettlementService,
                                    TradeStatusFlowService tradeStatusFlowService) {
        this(tradeOrderRepository, tradeOrderService, groupBuySettlementService, tradeStatusFlowService, null);
    }

    @Autowired
    public PaymentCompletionService(TradeOrderRepository tradeOrderRepository,
                                    TradeOrderService tradeOrderService,
                                    GroupBuySettlementService groupBuySettlementService,
                                    TradeStatusFlowService tradeStatusFlowService,
                                    UserQuotaService userQuotaService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderService = tradeOrderService;
        this.groupBuySettlementService = groupBuySettlementService;
        this.tradeStatusFlowService = tradeStatusFlowService;
        this.userQuotaService = userQuotaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCompletionResult complete(PaymentCompletionCommand command) {
        if (command == null) {
            throw new AppException("0001", "支付回调参数不能为空");
        }
        if (!StringUtils.hasText(command.getOrderId())) {
            throw new AppException("0001", "订单编号不能为空");
        }
        if (!StringUtils.hasText(command.getGatewayTradeNo())) {
            throw new AppException("0001", "外部交易单号不能为空");
        }

        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(command.getOrderId())
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(command.getOrderId())
                .orElseThrow(() -> new AppException("TRADE_0014", "支付单不存在"));

        if (PayStatusEnumVO.SUCCESS.equals(payOrder.getPayStatus())) {
            scheduleGrantQuotaAfterCommit(List.of(tradeOrder.getOrderId()));
            return toResult(tradeOrder, payOrder);
        }

        TradeOrderStatusEnumVO fromOrderStatus = tradeOrder.getOrderStatus();
        PayStatusEnumVO fromPayStatus = payOrder.getPayStatus();
        LocalDateTime payTime = command.getPayTime() == null ? LocalDateTime.now() : command.getPayTime();
        tradeOrderService.markPaySuccess(tradeOrder, payOrder, command.getGatewayTradeNo(), payTime);
        tradeOrderRepository.updatePaySuccess(tradeOrder, payOrder);
        recordPaySuccessFlow(tradeOrder, payOrder, fromOrderStatus, fromPayStatus);
        List<String> settledOrderIds = groupBuySettlementService.settlePaySuccess(tradeOrder);
        scheduleGrantQuotaAfterCommit(resolveGrantOrderIds(tradeOrder, settledOrderIds));

        return toResult(tradeOrder, payOrder);
    }

    private List<String> resolveGrantOrderIds(TradeOrderEntity tradeOrder, List<String> settledOrderIds) {
        if (settledOrderIds != null && !settledOrderIds.isEmpty()) {
            return settledOrderIds;
        }
        return List.of(tradeOrder.getOrderId());
    }

    private void scheduleGrantQuotaAfterCommit(List<String> orderIds) {
        if (userQuotaService == null || orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<String> targets = List.copyOf(new ArrayList<>(orderIds));
        Runnable grantTask = () -> {
            for (String orderId : targets) {
                tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                        .ifPresent(userQuotaService::grantQuotaForPaidOrder);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    grantTask.run();
                }
            });
            return;
        }
        grantTask.run();
    }

    private void recordPaySuccessFlow(TradeOrderEntity tradeOrder,
                                      PayOrderEntity payOrder,
                                      TradeOrderStatusEnumVO fromOrderStatus,
                                      PayStatusEnumVO fromPayStatus) {
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_ORDER,
                tradeOrder.getOrderId(),
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                fromOrderStatus,
                tradeOrder.getOrderStatus(),
                "order paid");
        tradeStatusFlowService.record(
                tradeOrder.getOrderId(),
                TradeStatusFlowService.BIZ_PAY,
                payOrder.getPayOrderId(),
                TradeStatusFlowService.EVENT_PAY_SUCCESS,
                fromPayStatus,
                payOrder.getPayStatus(),
                "pay success");
    }

    private PaymentCompletionResult toResult(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        PaymentCompletionResult result = new PaymentCompletionResult();
        result.setOrderId(tradeOrder.getOrderId());
        result.setPayOrderId(payOrder.getPayOrderId());
        result.setOrderStatus(tradeOrder.getOrderStatus().name());
        result.setPayStatus(payOrder.getPayStatus().name());
        result.setGatewayTradeNo(payOrder.getOutTradeNo());
        result.setPayTime(payOrder.getPayTime());
        return result;
    }
}
