package com.linrun.domain.account.service;

import com.linrun.api.dto.BillingPolicyDTO;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.QuotaFlowDTO;
import com.linrun.api.dto.QuotaSummaryResponse;
import com.linrun.api.dto.UserMembershipDTO;
import com.linrun.api.dto.UserModelConfigRequest;
import com.linrun.api.dto.UserModelConfigResponse;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserQuotaService {

    public static final String FLOW_ORDER_GRANT = "ORDER_GRANT";
    public static final String FLOW_REFUND_ROLLBACK = "REFUND_ROLLBACK";
    public static final String FLOW_TASK_CONSUME = "TASK_CONSUME";

    private static final BigDecimal MIN_TOKEN_COST = new BigDecimal("0.01");
    private static final BigDecimal DEFAULT_PROMPT_COST_PER_1K = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_COMPLETION_COST_PER_1K = new BigDecimal("0.30");
    private static final BigDecimal DEFAULT_CUSTOM_MODEL_SERVICE_RATE = new BigDecimal("0.10");
    private static final String DEFAULT_CUSTOM_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_CUSTOM_MODEL = "qwen3.6-plus";
    private static final String MEMBERSHIP_PLAN = "MEMBERSHIP_PLAN";

    private final UserQuotaRepository userQuotaRepository;
    private final GuideDataRepository guideDataRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final DynamicConfigService dynamicConfigService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${agent.user-model.crypto-secret:${AGENT_USER_MODEL_CRYPTO_SECRET:}}")
    private String modelConfigCryptoSecret = "";

    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            GuideDataRepository guideDataRepository,
                            TradeOrderRepository tradeOrderRepository) {
        this(userQuotaRepository, guideDataRepository, tradeOrderRepository, null);
    }

    @Autowired
    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            GuideDataRepository guideDataRepository,
                            TradeOrderRepository tradeOrderRepository,
                            DynamicConfigService dynamicConfigService) {
        this.userQuotaRepository = userQuotaRepository;
        this.guideDataRepository = guideDataRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.dynamicConfigService = dynamicConfigService;
    }

    public QuotaSummaryResponse querySummary(String userId, int limit) {
        validateUserId(userId);
        userQuotaRepository.createAccountIfAbsent(userId);
        QuotaSummaryResponse response = new QuotaSummaryResponse();
        response.setAccount(toAccountResponse(queryAccount(userId)));
        response.setFlows(userQuotaRepository.queryRecentFlows(userId, Math.max(1, Math.min(limit, 50))).stream()
                .map(this::toFlowDTO)
                .toList());
        response.setMembership(queryMembershipResponse(userId));
        response.setBillingPolicy(queryBillingPolicy());
        return response;
    }

    public QuotaAccountResponse queryAccountResponse(String userId) {
        validateUserId(userId);
        userQuotaRepository.createAccountIfAbsent(userId);
        return toAccountResponse(queryAccount(userId));
    }

    public UserMembershipDTO queryMembershipResponse(String userId) {
        validateUserId(userId);
        return toMembershipDTO(userQuotaRepository.queryMembership(userId).orElse(null), userId);
    }

    public BillingPolicyDTO queryBillingPolicy() {
        BillingPolicy policy = billingPolicy();
        BillingPolicyDTO dto = new BillingPolicyDTO();
        dto.setPlatformPromptCostPer1k(policy.promptCostPer1k());
        dto.setPlatformCompletionCostPer1k(policy.completionCostPer1k());
        dto.setCustomModelServiceRate(policy.customModelServiceRate());
        dto.setMemberCustomModelFree(true);
        dto.setUnit("quota_per_1k_tokens");
        return dto;
    }

    public UserModelConfigResponse queryModelConfigResponse(String userId) {
        validateUserId(userId);
        return toModelConfigResponse(userQuotaRepository.queryModelConfig(userId).orElse(null));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserModelConfigResponse saveModelConfig(String userId, UserModelConfigRequest request) {
        validateUserId(userId);
        UserModelConfigRequest safeRequest = request == null ? new UserModelConfigRequest() : request;
        UserModelConfig existing = userQuotaRepository.queryModelConfig(userId).orElse(null);
        boolean enabled = Boolean.TRUE.equals(safeRequest.getEnabled());
        String baseUrl = normalizeModelBaseUrl(firstText(safeRequest.getBaseUrl(),
                existing == null ? DEFAULT_CUSTOM_BASE_URL : existing.getBaseUrl()));
        String model = firstText(safeRequest.getModel(),
                existing == null ? DEFAULT_CUSTOM_MODEL : existing.getModel());
        String apiKey = safe(safeRequest.getApiKey()).trim();

        UserModelConfig modelConfig = new UserModelConfig();
        modelConfig.setUserId(userId);
        modelConfig.setEnabled(enabled);
        modelConfig.setBaseUrl(baseUrl);
        modelConfig.setModel(model);
        if (StringUtils.hasText(apiKey)) {
            modelConfig.setEncryptedApiKey(encryptApiKey(apiKey));
            modelConfig.setKeyMasked(maskApiKey(apiKey));
        } else if (existing != null) {
            modelConfig.setEncryptedApiKey(existing.getEncryptedApiKey());
            modelConfig.setKeyMasked(existing.getKeyMasked());
        }
        if (enabled && (!StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(model)
                || !StringUtils.hasText(modelConfig.getEncryptedApiKey()))) {
            throw new AppException("MODEL_CONFIG_0001", "请补全自定义模型地址、模型名和 API Key");
        }
        userQuotaRepository.upsertModelConfig(modelConfig);
        return queryModelConfigResponse(userId);
    }

    public Optional<UserModelConfig> queryRuntimeModelConfig(String userId) {
        validateUserId(userId);
        return userQuotaRepository.queryModelConfig(userId)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(config -> {
                    UserModelConfig runtime = new UserModelConfig();
                    runtime.setUserId(config.getUserId());
                    runtime.setEnabled(config.getEnabled());
                    runtime.setBaseUrl(config.getBaseUrl());
                    runtime.setModel(config.getModel());
                    runtime.setApiKey(decryptApiKey(config.getEncryptedApiKey()));
                    runtime.setKeyMasked(config.getKeyMasked());
                    runtime.setCreateTime(config.getCreateTime());
                    runtime.setUpdateTime(config.getUpdateTime());
                    return runtime;
                });
    }

    public boolean hasEnabledModelConfig(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        return userQuotaRepository.queryModelConfig(userId)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .filter(config -> StringUtils.hasText(config.getBaseUrl()))
                .filter(config -> StringUtils.hasText(config.getEncryptedApiKey()))
                .isPresent();
    }

    public void assertEnoughQuota(String userId, BigDecimal amount) {
        assertEnoughQuota(userId, amount, false);
    }

    public void assertEnoughQuota(String userId, BigDecimal amount, boolean customModelUsed) {
        validateUserId(userId);
        BigDecimal safeAmount = normalizeAmount(amount);
        if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (customModelUsed && activeMembership(userId).isPresent()) {
            return;
        }
        userQuotaRepository.createAccountIfAbsent(userId);
        UserQuotaAccount account = queryAccount(userId);
        BigDecimal available = account.getQuotaBalance().add(activeMembership(userId)
                .map(UserMembershipAccount::remainingQuota)
                .orElse(BigDecimal.ZERO));
        if (available.compareTo(safeAmount) < 0) {
            throw new AppException("QUOTA_0001", "额度不足，请先购买额度包或开通会员");
        }
    }

    public BigDecimal estimatePreCheckCost(String taskType) {
        return estimatePreCheckCost(taskType, false);
    }

    public BigDecimal estimatePreCheckCost(String taskType, boolean customModelUsed) {
        GuideTokenUsage sampleUsage = new GuideTokenUsage(500L, 500L, 1000L, BigDecimal.ZERO);
        return estimateTaskCost(sampleUsage, customModelUsed, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAcademicTask(String userId,
                                                       String sessionId,
                                                       String taskType,
                                                       GuideTokenUsage tokenUsage,
                                                       String model,
                                                       long latencyMillis) {
        return consumeForAcademicTask(userId, sessionId, buildTaskConsumeBizId(sessionId), taskType,
                tokenUsage, model, latencyMillis, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAcademicTask(String userId,
                                                       String sessionId,
                                                       String taskType,
                                                       GuideTokenUsage tokenUsage,
                                                       String model,
                                                       long latencyMillis,
                                                       boolean customModelUsed) {
        return consumeForAcademicTask(userId, sessionId, buildTaskConsumeBizId(sessionId), taskType,
                tokenUsage, model, latencyMillis, customModelUsed);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAcademicTask(String userId,
                                                       String sessionId,
                                                       String taskConsumeBizId,
                                                       String taskType,
                                                       GuideTokenUsage tokenUsage,
                                                       String model,
                                                       long latencyMillis) {
        return consumeForAcademicTask(userId, sessionId, taskConsumeBizId, taskType,
                tokenUsage, model, latencyMillis, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAcademicTask(String userId,
                                                       String sessionId,
                                                       String taskConsumeBizId,
                                                       String taskType,
                                                       GuideTokenUsage tokenUsage,
                                                       String model,
                                                       long latencyMillis,
                                                       boolean customModelUsed) {
        validateUserId(userId);
        userQuotaRepository.createAccountIfAbsent(userId);
        String safeTaskConsumeBizId = StringUtils.hasText(taskConsumeBizId)
                ? taskConsumeBizId.trim()
                : buildTaskConsumeBizId(sessionId);
        if (userQuotaRepository.queryFlow(userId, FLOW_TASK_CONSUME, safeTaskConsumeBizId).isPresent()) {
            return toAccountResponse(queryAccount(userId));
        }
        UserMembershipAccount membership = activeMembership(userId).orElse(null);
        boolean memberActive = membership != null;
        BigDecimal quotaCost = estimateTaskCost(tokenUsage, customModelUsed, memberActive);
        if (quotaCost.compareTo(BigDecimal.ZERO) <= 0) {
            userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
            return toAccountResponse(queryAccount(userId));
        }
        UserQuotaAccount before = queryAccount(userId);
        BigDecimal memberDebit = debitMembership(userId, quotaCost, membership);
        BigDecimal accountDebit = normalizeAmount(quotaCost.subtract(memberDebit));
        if (accountDebit.compareTo(BigDecimal.ZERO) > 0) {
            if (before.getQuotaBalance().compareTo(accountDebit) < 0) {
                throw new AppException("QUOTA_0001", "额度不足，请先购买额度包或开通会员");
            }
            int affected = userQuotaRepository.decreaseQuota(userId, accountDebit);
            if (affected <= 0) {
                throw new AppException("QUOTA_0001", "额度不足，请先购买额度包或开通会员");
            }
            UserQuotaAccount after = queryAccount(userId);
            userQuotaRepository.saveFlow(flow(userId, FLOW_TASK_CONSUME, safeTaskConsumeBizId,
                    accountDebit.negate(), before.getQuotaBalance(), after.getQuotaBalance(),
                    consumeRemark(taskType, quotaCost, memberDebit, customModelUsed)));
            userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
            return toAccountResponse(after);
        }
        UserQuotaAccount after = queryAccount(userId);
        userQuotaRepository.saveFlow(flow(userId, FLOW_TASK_CONSUME, safeTaskConsumeBizId,
                BigDecimal.ZERO, before.getQuotaBalance(), after.getQuotaBalance(),
                consumeRemark(taskType, quotaCost, memberDebit, customModelUsed)));
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
            markDealDoneIfNeeded(tradeOrder);
            return;
        }
        GuideProduct product = resolveOrderProduct(tradeOrder);
        if (isMembershipPlan(product)) {
            grantMembershipForPaidOrder(tradeOrder, product);
            return;
        }
        BigDecimal quotaAmount = resolveOrderQuota(tradeOrder, product);
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
        markDealDoneIfNeeded(tradeOrder);
    }

    private void grantMembershipForPaidOrder(TradeOrderEntity tradeOrder, GuideProduct product) {
        userQuotaRepository.createAccountIfAbsent(tradeOrder.getUserId());
        UserQuotaAccount before = queryAccount(tradeOrder.getUserId());
        UserMembershipAccount membership = buildMembership(tradeOrder.getUserId(), product);
        userQuotaRepository.upsertMembership(membership);
        UserQuotaAccount after = queryAccount(tradeOrder.getUserId());
        userQuotaRepository.saveFlow(flow(
                tradeOrder.getUserId(),
                FLOW_ORDER_GRANT,
                tradeOrder.getOrderId(),
                BigDecimal.ZERO,
                before.getQuotaBalance(),
                after.getQuotaBalance(),
                membershipGrantRemark(tradeOrder, membership)));
        markDealDoneIfNeeded(tradeOrder);
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

    private BigDecimal debitMembership(String userId, BigDecimal quotaCost, UserMembershipAccount membership) {
        if (membership == null || quotaCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal debit = membership.remainingQuota().min(quotaCost);
        debit = normalizeAmount(debit);
        if (debit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        int affected = userQuotaRepository.decreaseMembershipQuota(userId, debit);
        return affected > 0 ? debit : BigDecimal.ZERO;
    }

    private BigDecimal estimateTaskCost(GuideTokenUsage usage, boolean customModelUsed, boolean activeMember) {
        if (customModelUsed && activeMember) {
            return BigDecimal.ZERO;
        }
        GuideTokenUsage safeUsage = usage == null ? GuideTokenUsage.empty() : usage;
        long promptTokens = Math.max(0L, safeUsage.getPromptTokens());
        long completionTokens = Math.max(0L, safeUsage.getCompletionTokens());
        long totalTokens = Math.max(0L, safeUsage.getTotalTokens());
        if (promptTokens == 0L && completionTokens == 0L && totalTokens > 0L) {
            promptTokens = totalTokens;
        }
        boolean chargeable = promptTokens > 0L || completionTokens > 0L;
        if (!chargeable) {
            return BigDecimal.ZERO;
        }
        BillingPolicy policy = billingPolicy();
        BigDecimal cost = BigDecimal.valueOf(promptTokens).multiply(policy.promptCostPer1k())
                .add(BigDecimal.valueOf(completionTokens).multiply(policy.completionCostPer1k()))
                .divide(BigDecimal.valueOf(1000L), 6, RoundingMode.HALF_UP);
        if (customModelUsed) {
            cost = cost.multiply(policy.customModelServiceRate());
        }
        return normalizeCost(cost, true);
    }

    private BillingPolicy billingPolicy() {
        return new BillingPolicy(
                configDecimal(DynamicConfigService.AGENT_BILLING_PROMPT_COST_PER_1K, DEFAULT_PROMPT_COST_PER_1K),
                configDecimal(DynamicConfigService.AGENT_BILLING_COMPLETION_COST_PER_1K, DEFAULT_COMPLETION_COST_PER_1K),
                configDecimal(DynamicConfigService.AGENT_BILLING_CUSTOM_MODEL_SERVICE_RATE, DEFAULT_CUSTOM_MODEL_SERVICE_RATE));
    }

    private BigDecimal configDecimal(String key, BigDecimal fallback) {
        String fallbackText = fallback.toPlainString();
        String value = dynamicConfigService == null ? fallbackText : dynamicConfigService.getValue(key, fallbackText);
        try {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.compareTo(BigDecimal.ZERO) < 0 ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Optional<UserMembershipAccount> activeMembership(String userId) {
        LocalDateTime now = LocalDateTime.now();
        return userQuotaRepository.queryMembership(userId)
                .filter(membership -> membership.isActive(now));
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

    private void markDealDoneIfNeeded(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || TradeOrderStatusEnumVO.DEAL_DONE.equals(tradeOrder.getOrderStatus())) {
            return;
        }
        if (!isQuotaGrantable(tradeOrder)) {
            return;
        }
        tradeOrder.markDealDone();
        tradeOrderRepository.updateDealDone(tradeOrder);
    }

    private GuideProduct resolveOrderProduct(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getGoodsId())) {
            return null;
        }
        return guideDataRepository.queryProductByGoodsId(tradeOrder.getGoodsId()).orElse(null);
    }

    private BigDecimal resolveOrderQuota(TradeOrderEntity tradeOrder) {
        return resolveOrderQuota(tradeOrder, resolveOrderProduct(tradeOrder));
    }

    private BigDecimal resolveOrderQuota(TradeOrderEntity tradeOrder, GuideProduct product) {
        if (product != null && product.getQuotaAmount() != null && product.getQuotaAmount().compareTo(BigDecimal.ZERO) > 0) {
            return product.getQuotaAmount();
        }
        BigDecimal payAmount = tradeOrder.getPayAmount() == null ? BigDecimal.ZERO : tradeOrder.getPayAmount();
        return payAmount.multiply(BigDecimal.valueOf(20)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isMembershipPlan(GuideProduct product) {
        return product != null && MEMBERSHIP_PLAN.equals(product.getProductType());
    }

    private UserMembershipAccount buildMembership(String userId, GuideProduct product) {
        LocalDateTime now = LocalDateTime.now();
        UserMembershipAccount existing = userQuotaRepository.queryMembership(userId).orElse(null);
        LocalDateTime cycleStart = now;
        LocalDateTime cycleEnd = now.plusMonths(1);
        BigDecimal monthlyUsedQuota = BigDecimal.ZERO;
        if (existing != null
                && existing.isActive(now)
                && existing.getCycleEndTime() != null
                && product.getGoodsId().equals(existing.getPlanCode())) {
            cycleStart = existing.getCycleStartTime() == null ? now : existing.getCycleStartTime();
            cycleEnd = existing.getCycleEndTime().plusMonths(1);
            monthlyUsedQuota = existing.getMonthlyUsedQuota();
        }
        UserMembershipAccount membership = new UserMembershipAccount();
        membership.setUserId(userId);
        membership.setPlanCode(product.getGoodsId());
        membership.setPlanName(firstText(product.getGoodsName(), "会员套餐"));
        membership.setStatus("ACTIVE");
        membership.setMonthlyQuota(normalizeAmount(product.getQuotaAmount()));
        membership.setMonthlyUsedQuota(monthlyUsedQuota);
        membership.setCycleStartTime(cycleStart);
        membership.setCycleEndTime(cycleEnd);
        return membership;
    }

    private String grantRemark(TradeOrderEntity tradeOrder, BigDecimal quotaAmount) {
        String type = TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType()) ? "拼团购买" : "直接购买";
        return type + "额度包到账：" + quotaAmount.stripTrailingZeros().toPlainString();
    }

    private String membershipGrantRemark(TradeOrderEntity tradeOrder, UserMembershipAccount membership) {
        String type = TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType()) ? "拼团购买" : "直接购买";
        return type + "会员开通：" + firstText(membership.getPlanName(), "会员套餐")
                + "，月额度 " + membership.getMonthlyQuota().stripTrailingZeros().toPlainString();
    }

    private String consumeRemark(String taskType, BigDecimal quotaCost, BigDecimal memberDebit, boolean customModelUsed) {
        String source = customModelUsed ? "自定义模型" : "平台模型";
        if (memberDebit.compareTo(BigDecimal.ZERO) > 0) {
            return "任务按 token 扣费：" + safe(taskType) + "，" + source
                    + "，会员额度抵扣 " + memberDebit.stripTrailingZeros().toPlainString()
                    + "，总费用 " + quotaCost.stripTrailingZeros().toPlainString();
        }
        return "任务按 token 扣费：" + safe(taskType) + "，" + source
                + "，费用 " + quotaCost.stripTrailingZeros().toPlainString();
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

    private UserMembershipDTO toMembershipDTO(UserMembershipAccount membership, String userId) {
        LocalDateTime now = LocalDateTime.now();
        UserMembershipDTO dto = new UserMembershipDTO();
        dto.setUserId(userId);
        dto.setPlanCode(membership == null ? "FREE" : firstText(membership.getPlanCode(), "FREE"));
        dto.setPlanName(membership == null ? "免费版" : firstText(membership.getPlanName(), "免费版"));
        dto.setStatus(membership == null ? "INACTIVE" : firstText(membership.getStatus(), "INACTIVE"));
        dto.setMonthlyQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.getMonthlyQuota()));
        dto.setMonthlyUsedQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.getMonthlyUsedQuota()));
        dto.setRemainingMonthlyQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.remainingQuota()));
        dto.setCycleStartTime(membership == null ? null : membership.getCycleStartTime());
        dto.setCycleEndTime(membership == null ? null : membership.getCycleEndTime());
        dto.setActive(membership != null && membership.isActive(now));
        return dto;
    }

    private UserModelConfigResponse toModelConfigResponse(UserModelConfig modelConfig) {
        UserModelConfigResponse response = new UserModelConfigResponse();
        response.setEnabled(modelConfig != null && Boolean.TRUE.equals(modelConfig.getEnabled()));
        response.setBaseUrl(modelConfig == null ? DEFAULT_CUSTOM_BASE_URL : firstText(modelConfig.getBaseUrl(), DEFAULT_CUSTOM_BASE_URL));
        response.setModel(modelConfig == null ? DEFAULT_CUSTOM_MODEL : firstText(modelConfig.getModel(), DEFAULT_CUSTOM_MODEL));
        response.setKeyMasked(modelConfig == null ? "" : safe(modelConfig.getKeyMasked()));
        response.setUpdateTime(modelConfig == null ? null : modelConfig.getUpdateTime());
        return response;
    }

    private String normalizeModelBaseUrl(String value) {
        String text = safe(value).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.regionMatches(true, 0, "ttps://", 0, "ttps://".length())) {
            text = "h" + text;
        }
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            text = "https://" + text.replaceFirst("^/+", "");
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址格式不正确");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || !StringUtils.hasText(host)) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址仅支持 HTTPS");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址不能指向本地或内网地址");
        }
        return text.replaceAll("/+$", "");
    }

    private String encryptApiKey(String apiKey) {
        if (!StringUtils.hasText(modelConfigCryptoSecret)) {
            throw new AppException("MODEL_CONFIG_0003", "请先配置自定义模型密钥加密密钥");
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0004", "自定义模型密钥加密失败");
        }
    }

    private String decryptApiKey(String encryptedApiKey) {
        if (!StringUtils.hasText(encryptedApiKey)) {
            return "";
        }
        if (!StringUtils.hasText(modelConfigCryptoSecret)) {
            throw new AppException("MODEL_CONFIG_0003", "请先配置自定义模型密钥加密密钥");
        }
        String[] parts = encryptedApiKey.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new AppException("MODEL_CONFIG_0005", "自定义模型密钥格式不正确");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0005", "自定义模型密钥解密失败");
        }
    }

    private SecretKeySpec secretKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(modelConfigCryptoSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private String maskApiKey(String apiKey) {
        String text = safe(apiKey).trim();
        if (text.length() <= 8) {
            return "****";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }

    private BigDecimal normalizeCost(BigDecimal amount, boolean chargeable) {
        BigDecimal normalized = normalizeAmount(amount);
        if (chargeable && amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                && normalized.compareTo(BigDecimal.ZERO) == 0) {
            return MIN_TOKEN_COST;
        }
        return normalized;
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

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : safe(fallback);
    }

    private String buildTaskConsumeBizId(String sessionId) {
        String prefix = safe(sessionId).trim();
        if (prefix.length() > 36) {
            prefix = prefix.substring(0, 36);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
        return (StringUtils.hasText(prefix) ? prefix + "-" : "TASK-") + suffix;
    }

    private record BillingPolicy(BigDecimal promptCostPer1k,
                                 BigDecimal completionCostPer1k,
                                 BigDecimal customModelServiceRate) {
    }
}
