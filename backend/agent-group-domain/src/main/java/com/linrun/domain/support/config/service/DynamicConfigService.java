package com.linrun.domain.support.config.service;

import com.linrun.domain.support.config.adapter.DynamicConfigRepository;
import com.linrun.domain.support.config.event.DynamicConfigChangedEvent;
import com.linrun.domain.support.config.model.DynamicConfig;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicConfigService {

    public static final String DOWNGRADE_SWITCH = "downgradeSwitch";
    public static final String CUT_RANGE = "cutRange";
    public static final String SC_BLACKLIST = "scBlacklist";
    public static final String CACHE_SWITCH = "cacheSwitch";
    public static final String GROUP_SETTLEMENT_NOTIFY_TYPE = "groupSettlementNotifyType";
    public static final String GROUP_SETTLEMENT_NOTIFY_URL = "groupSettlementNotifyUrl";
    public static final String GROUP_SETTLEMENT_NOTIFY_MQ = "groupSettlementNotifyMQ";
    public static final String GROUP_REFUND_NOTIFY_TYPE = "groupRefundNotifyType";
    public static final String GROUP_REFUND_NOTIFY_URL = "groupRefundNotifyUrl";
    public static final String GROUP_REFUND_NOTIFY_MQ = "groupRefundNotifyMQ";
    public static final String PAYMENT_QUERY_COMPENSATION_SWITCH = "paymentQueryCompensationSwitch";
    public static final String PAYMENT_QUERY_COMPENSATION_LIMIT = "paymentQueryCompensationLimit";
    public static final String MOCK_PAY_SWITCH = "mockPaySwitch";
    public static final String PAYMENT_RISK_CHECK_SWITCH = "paymentRiskCheckSwitch";
    public static final String AGENT_PLAN_EXECUTE_SWITCH = "agentPlanExecuteSwitch";
    public static final String AGENT_CONTEXT_COMPACT_THRESHOLD = "agentContextCompactThreshold";
    public static final String KNOWLEDGE_CONTEXT_EXPANSION_SWITCH = "knowledgeContextExpansionSwitch";
    public static final String AGENT_BILLING_PROMPT_COST_PER_1K = "agentBillingPromptCostPer1k";
    public static final String AGENT_BILLING_COMPLETION_COST_PER_1K = "agentBillingCompletionCostPer1k";
    public static final String AGENT_BILLING_CUSTOM_MODEL_SERVICE_RATE = "agentBillingCustomModelServiceRate";

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(DOWNGRADE_SWITCH, "0"),
            Map.entry(CUT_RANGE, "100"),
            Map.entry(SC_BLACKLIST, ""),
            Map.entry(CACHE_SWITCH, "0"),
            Map.entry(GROUP_SETTLEMENT_NOTIFY_TYPE, "HTTP"),
            Map.entry(GROUP_SETTLEMENT_NOTIFY_URL, ""),
            Map.entry(GROUP_SETTLEMENT_NOTIFY_MQ, "agent.group.notify.group-settlement"),
            Map.entry(GROUP_REFUND_NOTIFY_TYPE, "HTTP"),
            Map.entry(GROUP_REFUND_NOTIFY_URL, ""),
            Map.entry(GROUP_REFUND_NOTIFY_MQ, "agent.group.notify.group-refund"),
            Map.entry(PAYMENT_QUERY_COMPENSATION_SWITCH, "1"),
            Map.entry(PAYMENT_QUERY_COMPENSATION_LIMIT, "50"),
            Map.entry(PAYMENT_RISK_CHECK_SWITCH, "1"),
            Map.entry(AGENT_PLAN_EXECUTE_SWITCH, "1"),
            Map.entry(AGENT_CONTEXT_COMPACT_THRESHOLD, "1600"),
            Map.entry(KNOWLEDGE_CONTEXT_EXPANSION_SWITCH, "1"),
            Map.entry(AGENT_BILLING_PROMPT_COST_PER_1K, "0.10"),
            Map.entry(AGENT_BILLING_COMPLETION_COST_PER_1K, "0.30"),
            Map.entry(AGENT_BILLING_CUSTOM_MODEL_SERVICE_RATE, "0.10")
    );

    private final DynamicConfigRepository dynamicConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, String> localCache = new ConcurrentHashMap<>();

    public DynamicConfigService(DynamicConfigRepository dynamicConfigRepository) {
        this(dynamicConfigRepository, null);
    }

    @Autowired
    public DynamicConfigService(DynamicConfigRepository dynamicConfigRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.dynamicConfigRepository = dynamicConfigRepository;
        this.eventPublisher = eventPublisher;
    }

    public DynamicConfig updateConfig(String key, String value) {
        if (!StringUtils.hasText(key)) {
            throw new AppException("DCC_0001", "config key cannot be blank");
        }
        if (value == null) {
            throw new AppException("DCC_0002", "config value cannot be null");
        }
        DynamicConfig config = DynamicConfig.of(key, value, "");
        dynamicConfigRepository.saveOrUpdate(config);
        localCache.put(key, value);
        publishConfigChanged(key, value);
        return queryConfig(key).orElse(config);
    }

    public void applyRemoteConfig(String key, String value) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        if (value == null) {
            localCache.remove(key);
        } else {
            localCache.put(key, value);
        }
    }

    public Optional<DynamicConfig> queryConfig(String key) {
        if (!StringUtils.hasText(key)) {
            throw new AppException("DCC_0001", "config key cannot be blank");
        }
        return dynamicConfigRepository.queryByKey(key)
                .or(() -> Optional.of(DynamicConfig.of(key, defaultValue(key), "default")));
    }

    public String getValue(String key, String fallback) {
        if (localCache.containsKey(key)) {
            return localCache.get(key);
        }
        String value = dynamicConfigRepository.queryByKey(key)
                .map(DynamicConfig::getConfigValue)
                .orElseGet(() -> defaultValue(key));
        if (!StringUtils.hasText(value)) {
            value = fallback;
        }
        localCache.put(key, value);
        return value;
    }

    public boolean isDowngradeSwitch() {
        return "1".equals(getValue(DOWNGRADE_SWITCH, "0"));
    }

    public boolean isCutRange(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        int range = parseInt(getValue(CUT_RANGE, "100"), 100);
        int bucket = Math.abs(userId.hashCode()) % 100;
        return bucket <= range;
    }

    public boolean isSourceChannelBlackIntercept(String source, String channel) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(channel)) {
            return false;
        }
        List<String> blacklist = Arrays.stream(getValue(SC_BLACKLIST, "").split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return blacklist.contains(source + channel);
    }

    public boolean isCacheOpenSwitch() {
        return "0".equals(getValue(CACHE_SWITCH, "0"));
    }

    public List<DynamicConfig> queryAllConfigs() {
        Map<String, DynamicConfig> merged = new java.util.LinkedHashMap<>();
        DEFAULTS.forEach((key, value) -> merged.put(key, DynamicConfig.of(key, value, "default")));
        dynamicConfigRepository.queryAll().forEach(config -> merged.put(config.getConfigKey(), config));
        return merged.values().stream()
                .sorted(java.util.Comparator.comparing(DynamicConfig::getConfigKey))
                .toList();
    }

    public boolean isPaymentQueryCompensationOpen() {
        return "1".equals(getValue(PAYMENT_QUERY_COMPENSATION_SWITCH, "1"));
    }

    public int paymentQueryCompensationLimit() {
        return Math.max(1, parseInt(getValue(PAYMENT_QUERY_COMPENSATION_LIMIT, "50"), 50));
    }

    public boolean isMockPayOpen() {
        return "1".equals(getValue(MOCK_PAY_SWITCH, "0"));
    }

    public boolean isPaymentRiskCheckOpen() {
        return "1".equals(getValue(PAYMENT_RISK_CHECK_SWITCH, "1"));
    }

    public boolean isAgentPlanExecuteOpen() {
        return "1".equals(getValue(AGENT_PLAN_EXECUTE_SWITCH, "1"));
    }

    public int agentContextCompactThreshold() {
        return Math.max(600, parseInt(getValue(AGENT_CONTEXT_COMPACT_THRESHOLD, "1600"), 1600));
    }

    public boolean isKnowledgeContextExpansionOpen() {
        return "1".equals(getValue(KNOWLEDGE_CONTEXT_EXPANSION_SWITCH, "1"));
    }

    private String defaultValue(String key) {
        return DEFAULTS.getOrDefault(key, "");
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void publishConfigChanged(String key, String value) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DynamicConfigChangedEvent(key, value));
        }
    }
}















