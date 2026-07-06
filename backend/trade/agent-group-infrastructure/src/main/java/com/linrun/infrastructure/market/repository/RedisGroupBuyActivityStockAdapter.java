package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.adapter.GroupBuyActivityStockPort;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RedisGroupBuyActivityStockAdapter implements GroupBuyActivityStockPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGroupBuyActivityStockAdapter.class);
    private static final Duration STOCK_TTL = Duration.ofHours(6);

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    public RedisGroupBuyActivityStockAdapter(RedissonClient redissonClient,
                                             @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryReserve(String activityId, String orderId, int dbAvailableStock) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(orderId)) {
            return true;
        }
        try {
            RAtomicLong available = redissonClient.getAtomicLong(availableKey(activityId));
            if (!available.isExists()) {
                available.set(Math.max(0, dbAvailableStock));
            }
            available.expire(STOCK_TTL);
            long after = available.decrementAndGet();
            if (after < 0) {
                available.incrementAndGet();
                return false;
            }
            redissonClient.getSet(reservedKey(activityId)).add(orderId);
            return true;
        } catch (Exception e) {
            LOGGER.warn("activity stock redis fallback, action=reserve, activityId={}, reason={}",
                    activityId, e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void release(String activityId, String orderId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(orderId)) {
            return;
        }
        try {
            RSet<String> reserved = redissonClient.getSet(reservedKey(activityId));
            if (!reserved.remove(orderId)) {
                return;
            }
            RAtomicLong available = redissonClient.getAtomicLong(availableKey(activityId));
            available.incrementAndGet();
            available.expire(STOCK_TTL);
        } catch (Exception e) {
            LOGGER.warn("activity stock redis fallback, action=release, activityId={}, reason={}",
                    activityId, e.getClass().getSimpleName());
        }
    }

    private String availableKey(String activityId) {
        return keyPrefix + ":group:activity-stock:available:" + activityId;
    }

    private String reservedKey(String activityId) {
        return keyPrefix + ":group:activity-stock:reserved:" + activityId;
    }
}
