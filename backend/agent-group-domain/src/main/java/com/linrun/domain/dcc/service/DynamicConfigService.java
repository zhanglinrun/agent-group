package com.linrun.domain.dcc.service;

import com.linrun.domain.dcc.adapter.DynamicConfigRepository;
import com.linrun.domain.dcc.model.DynamicConfig;
import com.linrun.types.exception.AppException;
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

    private static final Map<String, String> DEFAULTS = Map.of(
            DOWNGRADE_SWITCH, "0",
            CUT_RANGE, "100",
            SC_BLACKLIST, "",
            CACHE_SWITCH, "0",
            GROUP_SETTLEMENT_NOTIFY_TYPE, "HTTP",
            GROUP_SETTLEMENT_NOTIFY_URL, "",
            GROUP_SETTLEMENT_NOTIFY_MQ, "agent.group.notify.group-settlement"
    );

    private final DynamicConfigRepository dynamicConfigRepository;
    private final Map<String, String> localCache = new ConcurrentHashMap<>();

    public DynamicConfigService(DynamicConfigRepository dynamicConfigRepository) {
        this.dynamicConfigRepository = dynamicConfigRepository;
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
        return queryConfig(key).orElse(config);
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
}
