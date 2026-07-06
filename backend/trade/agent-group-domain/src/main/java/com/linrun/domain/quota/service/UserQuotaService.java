package com.linrun.domain.quota.service;

import com.linrun.api.dto.BillingPolicyDTO;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.QuotaFlowDTO;
import com.linrun.api.dto.QuotaSummaryResponse;
import com.linrun.api.dto.UserMembershipDTO;
import com.linrun.api.dto.UserModelConfigRequest;
import com.linrun.api.dto.UserModelConfigResponse;
import com.linrun.domain.quota.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.quota.model.UserQuotaAccount;
import com.linrun.domain.quota.model.UserQuotaFlow;
import com.linrun.domain.quota.adapter.QuotaProductRepository;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserQuotaService {

    public static final String FLOW_ORDER_GRANT = "ORDER_GRANT";
    public static final String FLOW_REFUND_ROLLBACK = "REFUND_ROLLBACK";
    public static final String FLOW_TASK_CONSUME = "TASK_CONSUME";

    private static final String DEFAULT_CUSTOM_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_CUSTOM_IMAGE_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_CUSTOM_TEXT_MODEL = "qwen3.7-plus";
    private static final String DEFAULT_CUSTOM_IMAGE_MODEL = "gpt-image-2";

    private final UserQuotaRepository userQuotaRepository;
    private final QuotaProductRepository quotaProductRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final DynamicConfigService dynamicConfigService;
    private final UserModelCredentialService userModelCredentialService;
    private final UserMembershipService membershipService;
    private final UserQuotaBillingService billingService;

    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            QuotaProductRepository quotaProductRepository,
                            TradeOrderRepository tradeOrderRepository) {
        this(userQuotaRepository, quotaProductRepository, tradeOrderRepository, null, null);
    }

    @Autowired
    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            QuotaProductRepository quotaProductRepository,
                            TradeOrderRepository tradeOrderRepository,
                            DynamicConfigService dynamicConfigService) {
        this(userQuotaRepository, quotaProductRepository, tradeOrderRepository, dynamicConfigService, null);
    }

    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            QuotaProductRepository quotaProductRepository,
                            TradeOrderRepository tradeOrderRepository,
                            DynamicConfigService dynamicConfigService,
                            UserModelCredentialService userModelCredentialService) {
        this(userQuotaRepository, quotaProductRepository, tradeOrderRepository, dynamicConfigService,
                userModelCredentialService, null, null);
    }

    public UserQuotaService(UserQuotaRepository userQuotaRepository,
                            QuotaProductRepository quotaProductRepository,
                            TradeOrderRepository tradeOrderRepository,
                            DynamicConfigService dynamicConfigService,
                            UserModelCredentialService userModelCredentialService,
                            UserMembershipService membershipService,
                            UserQuotaBillingService billingService) {
        this.userQuotaRepository = userQuotaRepository;
        this.quotaProductRepository = quotaProductRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.userModelCredentialService = userModelCredentialService == null
                ? new UserModelCredentialService()
                : userModelCredentialService;
        this.membershipService = membershipService == null
                ? new UserMembershipService(userQuotaRepository)
                : membershipService;
        this.billingService = billingService == null
                ? new UserQuotaBillingService(dynamicConfigService)
                : billingService;
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
        return membershipService.queryMembershipResponse(userId);
    }

    public BillingPolicyDTO queryBillingPolicy() {
        return billingService.queryBillingPolicy();
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
        String existingTextBaseUrl = existing == null
                ? DEFAULT_CUSTOM_BASE_URL
                : firstText(existing.getTextBaseUrl(), firstText(existing.getBaseUrl(), DEFAULT_CUSTOM_BASE_URL));
        String existingImageBaseUrl = existing == null
                ? DEFAULT_CUSTOM_IMAGE_BASE_URL
                : firstText(existing.getImageBaseUrl(), DEFAULT_CUSTOM_IMAGE_BASE_URL);
        String textBaseUrl = userModelCredentialService.normalizeModelBaseUrl(firstText(safeRequest.getTextBaseUrl(),
                firstText(safeRequest.getBaseUrl(), existingTextBaseUrl)));
        String imageBaseUrl = userModelCredentialService.normalizeModelBaseUrl(firstText(safeRequest.getImageBaseUrl(), existingImageBaseUrl));
        String existingTextModel = existing == null
                ? DEFAULT_CUSTOM_TEXT_MODEL
                : firstText(existing.getTextModel(), firstText(existing.getModel(), DEFAULT_CUSTOM_TEXT_MODEL));
        String textModel = firstText(safeRequest.getTextModel(),
                firstText(safeRequest.getModel(), existingTextModel));
        String imageModel = firstText(safeRequest.getImageModel(),
                existing == null ? DEFAULT_CUSTOM_IMAGE_MODEL : firstText(existing.getImageModel(), DEFAULT_CUSTOM_IMAGE_MODEL));
        String textApiKey = firstText(safeRequest.getTextApiKey(), safeRequest.getApiKey()).trim();
        String imageApiKey = safe(safeRequest.getImageApiKey()).trim();

        UserModelConfig modelConfig = new UserModelConfig();
        modelConfig.setUserId(userId);
        modelConfig.setEnabled(enabled);
        modelConfig.setBaseUrl(textBaseUrl);
        modelConfig.setTextBaseUrl(textBaseUrl);
        modelConfig.setImageBaseUrl(imageBaseUrl);
        modelConfig.setModel(textModel);
        modelConfig.setTextModel(textModel);
        modelConfig.setImageModel(imageModel);
        if (StringUtils.hasText(textApiKey)) {
            String encrypted = userModelCredentialService.encryptApiKey(textApiKey);
            String masked = userModelCredentialService.maskApiKey(textApiKey);
            modelConfig.setEncryptedApiKey(encrypted);
            modelConfig.setEncryptedTextApiKey(encrypted);
            modelConfig.setKeyMasked(masked);
            modelConfig.setTextKeyMasked(masked);
        } else if (existing != null) {
            String encrypted = firstText(existing.getEncryptedTextApiKey(), existing.getEncryptedApiKey());
            String masked = firstText(existing.getTextKeyMasked(), existing.getKeyMasked());
            modelConfig.setEncryptedApiKey(encrypted);
            modelConfig.setEncryptedTextApiKey(encrypted);
            modelConfig.setKeyMasked(masked);
            modelConfig.setTextKeyMasked(masked);
        }
        if (StringUtils.hasText(imageApiKey)) {
            modelConfig.setEncryptedImageApiKey(userModelCredentialService.encryptApiKey(imageApiKey));
            modelConfig.setImageKeyMasked(userModelCredentialService.maskApiKey(imageApiKey));
        } else if (existing != null) {
            modelConfig.setEncryptedImageApiKey(existing.getEncryptedImageApiKey());
            modelConfig.setImageKeyMasked(existing.getImageKeyMasked());
        }
        boolean textConfigReady = userModelCredentialService.modelConfigComplete(textBaseUrl, textModel, modelConfig.getEncryptedTextApiKey());
        boolean imageConfigReady = userModelCredentialService.modelConfigComplete(imageBaseUrl, imageModel, modelConfig.getEncryptedImageApiKey());
        if (enabled && !textConfigReady && !imageConfigReady) {
            throw new AppException("MODEL_CONFIG_0001", "请至少补全一套文本模型或图像模型 API 配置");
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
                    String textBaseUrl = firstText(config.getTextBaseUrl(), firstText(config.getBaseUrl(), DEFAULT_CUSTOM_BASE_URL));
                    runtime.setBaseUrl(textBaseUrl);
                    runtime.setTextBaseUrl(textBaseUrl);
                    runtime.setImageBaseUrl(firstText(config.getImageBaseUrl(), DEFAULT_CUSTOM_IMAGE_BASE_URL));
                    String textModel = firstText(config.getTextModel(), firstText(config.getModel(), DEFAULT_CUSTOM_TEXT_MODEL));
                    runtime.setModel(textModel);
                    runtime.setTextModel(textModel);
                    runtime.setImageModel(firstText(config.getImageModel(), DEFAULT_CUSTOM_IMAGE_MODEL));
                    String textApiKey = userModelCredentialService.decryptApiKey(firstText(config.getEncryptedTextApiKey(), config.getEncryptedApiKey()));
                    runtime.setApiKey(textApiKey);
                    runtime.setTextApiKey(textApiKey);
                    runtime.setImageApiKey(userModelCredentialService.decryptApiKey(config.getEncryptedImageApiKey()));
                    String textKeyMasked = firstText(config.getTextKeyMasked(), config.getKeyMasked());
                    runtime.setKeyMasked(textKeyMasked);
                    runtime.setTextKeyMasked(textKeyMasked);
                    runtime.setImageKeyMasked(config.getImageKeyMasked());
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
                .filter(config -> StringUtils.hasText(firstText(config.getTextBaseUrl(), config.getBaseUrl())))
                .filter(config -> StringUtils.hasText(firstText(config.getEncryptedTextApiKey(), config.getEncryptedApiKey())))
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
        if (customModelUsed && membershipService.findActive(userId).isPresent()) {
            return;
        }
        userQuotaRepository.createAccountIfAbsent(userId);
        UserQuotaAccount account = queryAccount(userId);
        BigDecimal available = account.getQuotaBalance().add(membershipService.remainingQuota(userId));
        if (available.compareTo(safeAmount) < 0) {
            throw new AppException("QUOTA_0001", "额度不足，请先购买额度包或开通会员");
        }
    }

    public BigDecimal estimatePreCheckCost(String taskType) {
        return estimatePreCheckCost(taskType, false);
    }

    public BigDecimal estimatePreCheckCost(String taskType, boolean customModelUsed) {
        return billingService.estimatePreCheckCost(taskType, customModelUsed);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAgentTask(String userId,
                                                       String sessionId,
                                                       String taskType,
                                                       TokenUsageMetrics tokenUsage,
                                                       String model,
                                                       long latencyMillis) {
        return consumeForAgentTask(userId, sessionId, buildTaskConsumeBizId(sessionId), taskType,
                tokenUsage, model, latencyMillis, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAgentTask(String userId,
                                                       String sessionId,
                                                       String taskType,
                                                       TokenUsageMetrics tokenUsage,
                                                       String model,
                                                       long latencyMillis,
                                                       boolean customModelUsed) {
        return consumeForAgentTask(userId, sessionId, buildTaskConsumeBizId(sessionId), taskType,
                tokenUsage, model, latencyMillis, customModelUsed);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAgentTask(String userId,
                                                       String sessionId,
                                                       String taskConsumeBizId,
                                                       String taskType,
                                                       TokenUsageMetrics tokenUsage,
                                                       String model,
                                                       long latencyMillis) {
        return consumeForAgentTask(userId, sessionId, taskConsumeBizId, taskType,
                tokenUsage, model, latencyMillis, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaAccountResponse consumeForAgentTask(String userId,
                                                       String sessionId,
                                                       String taskConsumeBizId,
                                                       String taskType,
                                                       TokenUsageMetrics tokenUsage,
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
        UserMembershipAccount membership = membershipService.findActive(userId).orElse(null);
        boolean memberActive = membership != null;
        BigDecimal quotaCost = billingService.estimateTaskCost(tokenUsage, customModelUsed, memberActive);
        if (quotaCost.compareTo(BigDecimal.ZERO) <= 0) {
            userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
            return toAccountResponse(queryAccount(userId));
        }
        UserQuotaAccount before = queryAccount(userId);
        BigDecimal memberDebit = membershipService.debit(userId, quotaCost, membership);
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
                    billingService.consumeRemark(taskType, quotaCost, memberDebit, customModelUsed)));
            userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
            return toAccountResponse(after);
        }
        UserQuotaAccount after = queryAccount(userId);
        userQuotaRepository.saveFlow(flow(userId, FLOW_TASK_CONSUME, safeTaskConsumeBizId,
                BigDecimal.ZERO, before.getQuotaBalance(), after.getQuotaBalance(),
                billingService.consumeRemark(taskType, quotaCost, memberDebit, customModelUsed)));
        userQuotaRepository.saveUsage(usage(userId, sessionId, taskType, tokenUsage, model, quotaCost, latencyMillis));
        return toAccountResponse(after);
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantQuotaForPaidOrder(TradeOrderEntity tradeOrder) {
        grantQuotaForPaidOrderInternal(tradeOrder);
    }

    private boolean grantQuotaForPaidOrderInternal(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getUserId()) || !StringUtils.hasText(tradeOrder.getOrderId())) {
            return false;
        }
        if (!isQuotaGrantable(tradeOrder)) {
            return false;
        }
        if (userQuotaRepository.queryFlow(tradeOrder.getUserId(), FLOW_ORDER_GRANT, tradeOrder.getOrderId()).isPresent()) {
            markDealDoneIfNeeded(tradeOrder);
            return true;
        }
        QuotaProduct product = resolveOrderProduct(tradeOrder);
        if (membershipService.isMembershipPlan(product)) {
            return grantMembershipForPaidOrder(tradeOrder, product);
        }
        BigDecimal quotaAmount = resolveOrderQuota(tradeOrder, product);
        if (quotaAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        userQuotaRepository.createAccountIfAbsent(tradeOrder.getUserId());
        UserQuotaAccount before = queryAccountForGrant(tradeOrder.getUserId());
        BigDecimal afterBalance = before.getQuotaBalance().add(quotaAmount);
        boolean recorded = recordGrantFlowOnce(flow(
                tradeOrder.getUserId(),
                FLOW_ORDER_GRANT,
                tradeOrder.getOrderId(),
                quotaAmount,
                before.getQuotaBalance(),
                afterBalance,
                grantRemark(tradeOrder, quotaAmount)));
        if (!recorded) {
            markDealDoneIfNeeded(tradeOrder);
            return true;
        }
        int affected = userQuotaRepository.increaseQuota(tradeOrder.getUserId(), quotaAmount);
        if (affected <= 0) {
            throw new AppException("QUOTA_0002", "额度发放失败，请稍后重试");
        }
        markDealDoneIfNeeded(tradeOrder);
        return true;
    }

    private boolean grantMembershipForPaidOrder(TradeOrderEntity tradeOrder, QuotaProduct product) {
        userQuotaRepository.createAccountIfAbsent(tradeOrder.getUserId());
        UserQuotaAccount before = queryAccountForGrant(tradeOrder.getUserId());
        UserMembershipAccount membership = membershipService.buildMembership(tradeOrder.getUserId(), product);
        boolean recorded = recordGrantFlowOnce(flow(
                tradeOrder.getUserId(),
                FLOW_ORDER_GRANT,
                tradeOrder.getOrderId(),
                BigDecimal.ZERO,
                before.getQuotaBalance(),
                before.getQuotaBalance(),
                membershipService.membershipGrantRemark(tradeOrder, membership)));
        if (!recorded) {
            markDealDoneIfNeeded(tradeOrder);
            return true;
        }
        membershipService.saveMembership(membership);
        markDealDoneIfNeeded(tradeOrder);
        return true;
    }

    /**
     * 写入发放流水作为幂等闸门。依赖 user_quota_flow 上的唯一约束
     * uk_user_biz_flow(user_id, flow_type, biz_id)：并发下只有一笔能写入成功，
     * 其余命中唯一键冲突，转成幂等结果返回，不会重复发放额度。
     */
    private boolean recordGrantFlowOnce(UserQuotaFlow grantFlow) {
        try {
            userQuotaRepository.saveFlow(grantFlow);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> grantQuotaForOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        List<String> processedOrderIds = orderIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(orderId -> tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                        .map(this::grantQuotaForPaidOrderInternal)
                        .orElse(false))
                .toList();
        return processedOrderIds;
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
        if (grantFlow == null) {
            return;
        }
        QuotaProduct product = resolveOrderProduct(tradeOrder);
        if (membershipService.isMembershipPlan(product)) {
            rollbackMembershipForRefundedOrder(tradeOrder, product);
            return;
        }
        if (grantFlow.getQuotaAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        UserQuotaAccount before = queryAccount(tradeOrder.getUserId());
        BigDecimal afterBalance = before.getQuotaBalance().subtract(grantFlow.getQuotaAmount());
        boolean recorded = recordRollbackFlowOnce(flow(
                tradeOrder.getUserId(),
                FLOW_REFUND_ROLLBACK,
                tradeOrder.getOrderId(),
                grantFlow.getQuotaAmount().negate(),
                before.getQuotaBalance(),
                afterBalance,
                "订单退款回滚额度"));
        if (!recorded) {
            return;
        }
        int affected = userQuotaRepository.decreaseQuotaAllowNegative(tradeOrder.getUserId(), grantFlow.getQuotaAmount());
        if (affected <= 0) {
            throw new AppException("QUOTA_0003", "退款回滚额度失败，请稍后重试");
        }
    }

    private void rollbackMembershipForRefundedOrder(TradeOrderEntity tradeOrder, QuotaProduct product) {
        UserMembershipAccount membership = userQuotaRepository.queryMembership(tradeOrder.getUserId()).orElse(null);
        UserQuotaAccount account = queryAccount(tradeOrder.getUserId());
        boolean recorded = recordRollbackFlowOnce(flow(
                tradeOrder.getUserId(),
                FLOW_REFUND_ROLLBACK,
                tradeOrder.getOrderId(),
                BigDecimal.ZERO,
                account.getQuotaBalance(),
                account.getQuotaBalance(),
                "订单退款撤销会员：" + firstText(membership == null ? "" : membership.getPlanName(), "会员套餐")));
        if (!recorded) {
            return;
        }
        membershipService.rollbackMembershipState(tradeOrder.getUserId(), product);
    }

    private boolean recordRollbackFlowOnce(UserQuotaFlow rollbackFlow) {
        try {
            userQuotaRepository.saveFlow(rollbackFlow);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private UserQuotaAccount queryAccount(String userId) {
        return userQuotaRepository.queryAccount(userId)
                .orElseThrow(() -> new AppException("QUOTA_0002", "额度账户不存在"));
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

    private QuotaProduct resolveOrderProduct(TradeOrderEntity tradeOrder) {
        if (tradeOrder == null || !StringUtils.hasText(tradeOrder.getGoodsId())) {
            return null;
        }
        return quotaProductRepository.queryProductByGoodsId(tradeOrder.getGoodsId()).orElse(null);
    }

    private BigDecimal resolveOrderQuota(TradeOrderEntity tradeOrder) {
        return resolveOrderQuota(tradeOrder, resolveOrderProduct(tradeOrder));
    }

    private BigDecimal resolveOrderQuota(TradeOrderEntity tradeOrder, QuotaProduct product) {
        if (product != null && product.getQuotaAmount() != null && product.getQuotaAmount().compareTo(BigDecimal.ZERO) > 0) {
            return product.getQuotaAmount();
        }
        throw new AppException("QUOTA_0003", "商品未配置额度，拒绝发放");
    }

    private UserQuotaAccount queryAccountForGrant(String userId) {
        return userQuotaRepository.queryAccountForUpdate(userId)
                .orElseGet(() -> userQuotaRepository.queryAccount(userId)
                        .orElseThrow(() -> new AppException("QUOTA_0001", "额度账户不存在")));
    }

    private String grantRemark(TradeOrderEntity tradeOrder, BigDecimal quotaAmount) {
        String type = TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType()) ? "拼团购买" : "直接购买";
        return type + "额度包到账：" + quotaAmount.stripTrailingZeros().toPlainString();
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
                                   TokenUsageMetrics tokenUsage,
                                   String model,
                                   BigDecimal quotaCost,
                                   long latencyMillis) {
        TokenUsageMetrics safeUsage = tokenUsage == null ? TokenUsageMetrics.empty() : tokenUsage;
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

    private UserModelConfigResponse toModelConfigResponse(UserModelConfig modelConfig) {
        UserModelConfigResponse response = new UserModelConfigResponse();
        response.setEnabled(modelConfig != null && Boolean.TRUE.equals(modelConfig.getEnabled()));
        String textBaseUrl = modelConfig == null
                ? DEFAULT_CUSTOM_BASE_URL
                : firstText(modelConfig.getTextBaseUrl(), firstText(modelConfig.getBaseUrl(), DEFAULT_CUSTOM_BASE_URL));
        response.setBaseUrl(textBaseUrl);
        response.setTextBaseUrl(textBaseUrl);
        String textModel = modelConfig == null
                ? DEFAULT_CUSTOM_TEXT_MODEL
                : firstText(modelConfig.getTextModel(), firstText(modelConfig.getModel(), DEFAULT_CUSTOM_TEXT_MODEL));
        response.setModel(textModel);
        response.setTextModel(textModel);
        response.setImageBaseUrl(modelConfig == null
                ? DEFAULT_CUSTOM_IMAGE_BASE_URL
                : firstText(modelConfig.getImageBaseUrl(), DEFAULT_CUSTOM_IMAGE_BASE_URL));
        response.setImageModel(modelConfig == null
                ? DEFAULT_CUSTOM_IMAGE_MODEL
                : firstText(modelConfig.getImageModel(), DEFAULT_CUSTOM_IMAGE_MODEL));
        String textKeyMasked = modelConfig == null ? "" : firstText(modelConfig.getTextKeyMasked(), modelConfig.getKeyMasked());
        response.setKeyMasked(textKeyMasked);
        response.setTextKeyMasked(textKeyMasked);
        response.setImageKeyMasked(modelConfig == null ? "" : safe(modelConfig.getImageKeyMasked()));
        response.setUpdateTime(modelConfig == null ? null : modelConfig.getUpdateTime());
        return response;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : safe(fallback);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new AppException("0001", "用户编号不能为空");
        }
    }

    private String buildTaskConsumeBizId(String sessionId) {
        String prefix = safe(sessionId).trim();
        if (prefix.length() > 36) {
            prefix = prefix.substring(0, 36);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
        return (StringUtils.hasText(prefix) ? prefix + "-" : "TASK-") + suffix;
    }
}













