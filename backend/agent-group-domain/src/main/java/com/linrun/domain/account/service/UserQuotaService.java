package com.linrun.domain.account.service;

import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.QuotaFlowDTO;
import com.linrun.api.dto.QuotaSummaryResponse;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserQuotaService {

    public static final String FLOW_ORDER_GRANT = "ORDER_GRANT";
    public static final String FLOW_REFUND_ROLLBACK = "REFUND_ROLLBACK";
    public static final String FLOW_TASK_CONSUME = "TASK_CONSUME";

    private final UserQuotaRepository userQuotaRepository;
    private final GuideDataRepository guideDataRepository;
    private final TradeOrderRepository tradeOrderRepository;

    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            GuideDataRepository guideDataRepository,
                            TradeOrderRepository tradeOrderRepository) {
        this.userQuotaRepository = userQuotaRepository;
        this.guideDataRepository = guideDataRepository;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    public QuotaSummaryResponse querySummary(String userId, int limit) {
        validateUserId(userId);
        userQuotaRepository.createAccountIfAbsent(userId);
        QuotaSummaryResponse response = new QuotaSummaryResponse();
        response.setAccount(toAccountResponse(queryAccount(userId)));
        response.setFlows(userQuotaRepository.queryRecentFlows(userId, Math.max(1, Math.min(limit, 50))).stream()
                .map(this::toFlowDTO)
                .toList());
        return response;
    }

    public QuotaAccountResponse queryAccountResponse(String userId) {
        validateUserId(userId);
        userQuotaRepository.createAccountIfAbsent(userId);
        return toAccountResponse(queryAccount(userId));
    }

    public void assertEnoughQuota(String userId, BigDecimal amount) {
        validateUserId(userId);
        BigDecimal safeAmount = normalizeAmount(amount);
        if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        userQuotaRepository.createAccountIfAbsent(userId);
        UserQuotaAccount account = queryAccount(userId);
        if (account.getQuotaBalance().compareTo(safeAmount) < 0) {
            throw new AppException("QUOTA_0001", "额度不足，请先购买或拼团充值额度");
        }
    }

    public BigDecimal estimatePreCheckCost(String taskType) {
        return taskBaseCost(taskType);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAcademicTask(String userId,
                                                       String sessionId,
                                                       String taskType,
                                                       GuideTokenUsage tokenUsage,
                                                       String model,
                                                       long latencyMillis) {
        validateUserId(userId);
        BigDecimal quotaCost = estimateTaskCost(taskType, tokenUsage);
        assertEnoughQuota(userId, quotaCost);
        UserQuotaAccount before = queryAccount(userId);
        int affected = userQuotaRepository.decreaseQuota(userId, quotaCost);
        if (affected <= 0) {
            throw new AppException("QUOTA_0001", "额度不足，请先购买或拼团充值额度");
        }
        UserQuotaAccount after = queryAccount(userId);
        UserQuotaFlow flow = flow(userId, FLOW_TASK_CONSUME, sessionId, quotaCost.negate(), before.getQuotaBalance(),
                after.getQuotaBalance(), "学术任务消耗额度：" + safe(taskType));
        userQuotaRepository.saveFlow(flow);
        userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
        return toAccountResponse(after);
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantQuotaForPaidOrder(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getUserId()) || !StringUtils.hasText(tradeOrder.getOrderId())) {
            return;
        }
        if (!isQuotaGrantable(tradeOrder)) {
            return;
        }
        if (userQuotaRepository.queryFlow(tradeOrder.getUserId(), FLOW_ORDER_GRANT, tradeOrder.getOrderId()).isPresent()) {
            return;
        }
        BigDecimal quotaAmount = resolveOrderQuota(tradeOrder);
        if (quotaAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        userQuotaRepository.createAccountIfAbsent(tradeOrder.getUserId());
        UserQuotaAccount before = queryAccount(tradeOrder.getUserId());
        userQuotaRepository.increaseQuota(tradeOrder.getUserId(), quotaAmount);
        UserQuotaAccount after = queryAccount(tradeOrder.getUserId());
        userQuotaRepository.saveFlow(flow(
                tradeOrder.getUserId(),
                FLOW_ORDER_GRANT,
                tradeOrder.getOrderId(),
                quotaAmount,
                before.getQuotaBalance(),
                after.getQuotaBalance(),
                grantRemark(tradeOrder, quotaAmount)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantQuotaForOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        orderIds.stream()
                .filter(StringUtils::hasText)
                .map(orderId -> tradeOrderRepository.queryTradeOrderByOrderId(orderId).orElse(null))
                .forEach(this::grantQuotaForPaidOrder);
    }

    private boolean isQuotaGrantable(TradeOrderEntity tradeOrder) {
        TradeOrderStatusEnumVO status = tradeOrder.getOrderStatus();
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType())) {
            return TradeOrderStatusEnumVO.GROUP_SETTLED.equals(status)
                    || TradeOrderStatusEnumVO.DEAL_DONE.equals(status);
        }
        return TradeOrderStatusEnumVO.PAY_SUCCESS.equals(status)
                || TradeOrderStatusEnumVO.DEAL_DONE.equals(status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollbackQuotaForRefundedOrder(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getUserId()) || !StringUtils.hasText(tradeOrder.getOrderId())) {
            return;
        }
        if (userQuotaRepository.queryFlow(tradeOrder.getUserId(), FLOW_REFUND_ROLLBACK, tradeOrder.getOrderId()).isPresent()) {
            return;
        }
        UserQuotaFlow grantFlow = userQuotaRepository.queryFlow(tradeOrder.getUserId(), FLOW_ORDER_GRANT, tradeOrder.getOrderId())
                .orElse(null);
        if (grantFlow == null || grantFlow.getQuotaAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        UserQuotaAccount before = queryAccount(tradeOrder.getUserId());
        userQuotaRepository.decreaseQuotaAllowNegative(tradeOrder.getUserId(), grantFlow.getQuotaAmount());
        UserQuotaAccount after = queryAccount(tradeOrder.getUserId());
        userQuotaRepository.saveFlow(flow(
                tradeOrder.getUserId(),
                FLOW_REFUND_ROLLBACK,
                tradeOrder.getOrderId(),
                grantFlow.getQuotaAmount().negate(),
                before.getQuotaBalance(),
                after.getQuotaBalance(),
                "订单退款回滚额度"));
    }

    private BigDecimal resolveOrderQuota(TradeOrderEntity tradeOrder) {
        GuideProduct product = guideDataRepository.queryProductByGoodsId(tradeOrder.getGoodsId()).orElse(null);
        if (product != null && product.getQuotaAmount() != null && product.getQuotaAmount().compareTo(BigDecimal.ZERO) > 0) {
            return product.getQuotaAmount();
        }
        BigDecimal payAmount = tradeOrder.getPayAmount() == null ? BigDecimal.ZERO : tradeOrder.getPayAmount();
        return payAmount.multiply(BigDecimal.valueOf(20)).setScale(2, RoundingMode.HALF_UP);
    }

    private String grantRemark(TradeOrderEntity tradeOrder, BigDecimal quotaAmount) {
        String type = TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType()) ? "拼团购买" : "直接购买";
        return type + "额度包到账：" + quotaAmount.stripTrailingZeros().toPlainString() + " 次";
    }

    private BigDecimal estimateTaskCost(String taskType, GuideTokenUsage usage) {
        BigDecimal base = taskBaseCost(taskType);
        long totalTokens = usage == null ? 0L : Math.max(0L, usage.getTotalTokens());
        if (totalTokens <= 5000L) {
            return base;
        }
        long extra = (long) Math.ceil((totalTokens - 5000L) / 5000.0d);
        return base.add(BigDecimal.valueOf(extra));
    }

    private BigDecimal taskBaseCost(String taskType) {
        String type = safe(taskType).toLowerCase();
        if (type.contains("ppt")) {
            return BigDecimal.valueOf(8);
        }
        if (type.contains("image") || type.contains("diagram")) {
            return BigDecimal.valueOf(4);
        }
        if (type.contains("file") || type.contains("paper") || type.contains("summary")) {
            return BigDecimal.valueOf(2);
        }
        if (type.contains("code") || type.contains("repo") || type.contains("deep")) {
            return BigDecimal.valueOf(3);
        }
        return BigDecimal.ONE;
    }

    private UserQuotaAccount queryAccount(String userId) {
        return userQuotaRepository.queryAccount(userId)
                .orElseThrow(() -> new AppException("QUOTA_0002", "额度账户不存在"));
    }

    private UserQuotaFlow flow(String userId,
                               String flowType,
                               String bizId,
                               BigDecimal amount,
                               BigDecimal beforeBalance,
                               BigDecimal afterBalance,
                               String remark) {
        UserQuotaFlow flow = new UserQuotaFlow();
        flow.setFlowId("QF" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        flow.setUserId(userId);
        flow.setFlowType(flowType);
        flow.setBizId(safe(bizId));
        flow.setQuotaAmount(normalizeAmount(amount));
        flow.setBeforeBalance(normalizeAmount(beforeBalance));
        flow.setAfterBalance(normalizeAmount(afterBalance));
        flow.setRemark(safe(remark));
        flow.setCreateTime(LocalDateTime.now());
        return flow;
    }

    private ModelUsageRecord usage(String userId,
                                   String sessionId,
                                   String taskType,
                                   GuideTokenUsage tokenUsage,
                                   String model,
                                   BigDecimal quotaCost,
                                   long latencyMillis) {
        GuideTokenUsage safeUsage = tokenUsage == null ? GuideTokenUsage.empty() : tokenUsage;
        ModelUsageRecord usage = new ModelUsageRecord();
        usage.setUsageId("MU" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        usage.setUserId(userId);
        usage.setSessionId(safe(sessionId));
        usage.setTaskType(safe(taskType));
        usage.setModel(safe(model));
        usage.setPromptTokens(safeUsage.getPromptTokens());
        usage.setCompletionTokens(safeUsage.getCompletionTokens());
        usage.setTotalTokens(safeUsage.getTotalTokens());
        usage.setQuotaCost(normalizeAmount(quotaCost));
        usage.setLatencyMillis(Math.max(0L, latencyMillis));
        usage.setCreateTime(LocalDateTime.now());
        return usage;
    }

    private QuotaAccountResponse toAccountResponse(UserQuotaAccount account) {
        QuotaAccountResponse response = new QuotaAccountResponse();
        response.setUserId(account.getUserId());
        response.setQuotaBalance(account.getQuotaBalance());
        response.setFrozenQuota(account.getFrozenQuota());
        response.setUsedQuota(account.getUsedQuota());
        return response;
    }

    private QuotaFlowDTO toFlowDTO(UserQuotaFlow flow) {
        QuotaFlowDTO dto = new QuotaFlowDTO();
        dto.setFlowId(flow.getFlowId());
        dto.setUserId(flow.getUserId());
        dto.setFlowType(flow.getFlowType());
        dto.setBizId(flow.getBizId());
        dto.setQuotaAmount(flow.getQuotaAmount());
        dto.setBeforeBalance(flow.getBeforeBalance());
        dto.setAfterBalance(flow.getAfterBalance());
        dto.setRemark(flow.getRemark());
        dto.setCreateTime(flow.getCreateTime());
        return dto;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new AppException("0001", "用户编号不能为空");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
