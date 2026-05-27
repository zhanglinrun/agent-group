package com.linrun.infrastructure.marketing.repository;

import com.linrun.domain.marketing.adapter.GroupBuyTeamStockRepository;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisGroupBuyTeamStockRepository implements GroupBuyTeamStockRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGroupBuyTeamStockRepository.class);
    private static final Duration DEFAULT_STOCK_TTL = Duration.ofHours(2);
    private static final Duration RECOVERY_LOCK_TTL = Duration.ofDays(30);

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    public RedisGroupBuyTeamStockRepository(RedissonClient redissonClient,
                                            @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean occupyTeamStock(String activityId, String teamId, Integer targetCount, LocalDateTime validEndTime) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(teamId) || targetCount == null || targetCount <= 0) {
            return true;
        }
        String stockKey = teamStockKey(activityId, teamId);
        String recoveryKey = recoveryTeamStockKey(activityId, teamId);
        Duration ttl = stockTtl(validEndTime);
        try {
            RAtomicLong recoveryCounter = redissonClient.getAtomicLong(recoveryKey);
            long recoveryCount = recoveryCounter.get();
            RAtomicLong stockCounter = redissonClient.getAtomicLong(stockKey);
            long occupy = stockCounter.incrementAndGet() + 1L;
            stockCounter.expire(ttl);
            recoveryCounter.expire(ttl);
            if (occupy > targetCount + recoveryCount) {
                stockCounter.decrementAndGet();
                return false;
            }
            String lockKey = stockKey + ":" + occupy;
            return redissonClient.<String>getBucket(lockKey).trySet("1", ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("group team stock redis fallback, action=occupy, teamId={}, reason={}",
                    teamId, e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void recoverTeamStock(String activityId, String teamId, String orderId, LocalDateTime validEndTime) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(teamId) || !StringUtils.hasText(orderId)) {
            return;
        }
        String lockKey = recoveryLockKey(orderId);
        try {
            RBucket<String> recoveryLock = redissonClient.getBucket(lockKey);
            boolean locked = recoveryLock.trySet("1", RECOVERY_LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                return;
            }
            String recoveryKey = recoveryTeamStockKey(activityId, teamId);
            RAtomicLong recoveryCounter = redissonClient.getAtomicLong(recoveryKey);
            recoveryCounter.incrementAndGet();
            recoveryCounter.expire(stockTtl(validEndTime));
        } catch (Exception e) {
            try {
                redissonClient.getBucket(lockKey).delete();
            } catch (Exception ignored) {
                LOGGER.warn("group team stock redis recovery lock cleanup failed, orderId={}", orderId);
            }
            LOGGER.warn("group team stock redis fallback, action=recover, teamId={}, orderId={}, reason={}",
                    teamId, orderId, e.getClass().getSimpleName());
        }
    }

    private Duration stockTtl(LocalDateTime validEndTime) {
        if (validEndTime == null) {
            return DEFAULT_STOCK_TTL;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), validEndTime).plusHours(1);
        return ttl.isNegative() || ttl.isZero() ? DEFAULT_STOCK_TTL : ttl;
    }

    private String teamStockKey(String activityId, String teamId) {
        return keyPrefix + ":group:team-stock:" + activityId + ":" + teamId;
    }

    private String recoveryTeamStockKey(String activityId, String teamId) {
        return teamStockKey(activityId, teamId) + ":recovery";
    }

    private String recoveryLockKey(String orderId) {
        return keyPrefix + ":group:team-stock:recovery-lock:" + orderId;
    }
}
