package com.linrun.domain.quota.service;

import com.linrun.api.dto.BillingPolicyDTO;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.domain.support.config.service.DynamicConfigService;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Agent 任务 token 计费策略与费用估算。
 */
public class UserQuotaBillingService {

    private static final BigDecimal MIN_TOKEN_COST = new BigDecimal("0.01");
    private static final BigDecimal DEFAULT_PROMPT_COST_PER_1K = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_COMPLETION_COST_PER_1K = new BigDecimal("0.30");
    private static final BigDecimal DEFAULT_CUSTOM_MODEL_SERVICE_RATE = new BigDecimal("0.10");

    private final DynamicConfigService dynamicConfigService;

    public UserQuotaBillingService(DynamicConfigService dynamicConfigService) {
        this.dynamicConfigService = dynamicConfigService;
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

    public BigDecimal estimatePreCheckCost(String taskType, boolean customModelUsed) {
        TokenUsageMetrics sampleUsage = new TokenUsageMetrics(500L, 500L, 1000L, BigDecimal.ZERO);
        return estimateTaskCost(sampleUsage, customModelUsed, false);
    }

    public BigDecimal estimateTaskCost(TokenUsageMetrics usage, boolean customModelUsed, boolean activeMember) {
        if (customModelUsed && activeMember) {
            return BigDecimal.ZERO;
        }
        TokenUsageMetrics safeUsage = usage == null ? TokenUsageMetrics.empty() : usage;
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

    public String consumeRemark(String taskType, BigDecimal quotaCost, BigDecimal memberDebit, boolean customModelUsed) {
        String source = customModelUsed ? "自定义模型" : "平台模型";
        if (memberDebit.compareTo(BigDecimal.ZERO) > 0) {
            return "任务 token 扣费：" + safe(taskType) + "，" + source
                    + "，会员额度抵扣 " + memberDebit.stripTrailingZeros().toPlainString()
                    + "，总费用 " + quotaCost.stripTrailingZeros().toPlainString();
        }
        return "任务 token 扣费：" + safe(taskType) + "，" + source
                + "，费用 " + quotaCost.stripTrailingZeros().toPlainString();
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record BillingPolicy(BigDecimal promptCostPer1k,
                                 BigDecimal completionCostPer1k,
                                 BigDecimal customModelServiceRate) {
    }
}
