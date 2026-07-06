package com.linrun.infrastructure.market.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.support.config.service.DynamicConfigService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 拼团试算读路径 Redis 缓存，Redis 异常时 fail-open 回源 DB。
 */
@Component
public class RedisGroupBuyMarketReadCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGroupBuyMarketReadCache.class);
    private static final String EMPTY_MARKER = "__EMPTY__";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final DynamicConfigService dynamicConfigService;
    private final String keyPrefix;
    private final Duration ttl;
    private final boolean enabled;

    public RedisGroupBuyMarketReadCache(RedissonClient redissonClient,
                                        ObjectMapper objectMapper,
                                        @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix,
                                        @Value("${agent.group.market.cache.ttl-seconds:300}") long ttlSeconds,
                                        @Value("${agent.group.market.cache.enabled:true}") boolean enabled,
                                        @Autowired(required = false) DynamicConfigService dynamicConfigService) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.dynamicConfigService = dynamicConfigService;
        this.keyPrefix = keyPrefix;
        this.ttl = Duration.ofSeconds(Math.max(30L, ttlSeconds));
        this.enabled = enabled;
    }

    public <T> Optional<T> getOrLoad(String cacheKey, Class<T> type, Supplier<Optional<T>> loader) {
        if (!isCacheEffective() || !StringUtils.hasText(cacheKey)) {
            return loader.get();
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(fullKey(cacheKey));
            String cached = bucket.get();
            if (cached != null) {
                if (EMPTY_MARKER.equals(cached)) {
                    return Optional.empty();
                }
                return Optional.ofNullable(objectMapper.readValue(cached, type));
            }
        } catch (Exception e) {
            LOGGER.warn("group market cache read fallback, key={}, reason={}", cacheKey, e.getClass().getSimpleName());
            return loader.get();
        }

        Optional<T> loaded = loader.get();
        try {
            RBucket<String> bucket = redissonClient.getBucket(fullKey(cacheKey));
            if (loaded.isEmpty()) {
                bucket.set(EMPTY_MARKER, ttl);
            } else {
                bucket.set(objectMapper.writeValueAsString(loaded.get()), ttl);
            }
        } catch (JsonProcessingException e) {
            LOGGER.warn("group market cache write skip, key={}, reason={}", cacheKey, e.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.warn("group market cache write fallback, key={}, reason={}", cacheKey, e.getClass().getSimpleName());
        }
        return loaded;
    }

    public void evict(String cacheKey) {
        if (!isCacheEffective() || !StringUtils.hasText(cacheKey)) {
            return;
        }
        try {
            redissonClient.getBucket(fullKey(cacheKey)).delete();
        } catch (Exception e) {
            LOGGER.warn("group market cache evict fallback, key={}, reason={}", cacheKey, e.getClass().getSimpleName());
        }
    }

    public static String skuKey(String goodsId) {
        return "group:market:sku:" + goodsId;
    }

    public static String sourceChannelKey(String source, String channel, String goodsId) {
        return "group:market:sc:" + source + ":" + channel + ":" + goodsId;
    }

    public static String discountKey(String discountId) {
        return "group:market:discount:" + discountId;
    }

    public static String activityByIdKey(String activityId) {
        return "group:market:activity:id:" + activityId;
    }

    public static String activityByGoodsKey(String goodsId) {
        return "group:market:activity:goods:" + goodsId;
    }

    private String fullKey(String cacheKey) {
        return keyPrefix + ":" + cacheKey;
    }

    private boolean isCacheEffective() {
        if (!enabled) {
            return false;
        }
        return dynamicConfigService == null || dynamicConfigService.isCacheOpenSwitch();
    }
}
