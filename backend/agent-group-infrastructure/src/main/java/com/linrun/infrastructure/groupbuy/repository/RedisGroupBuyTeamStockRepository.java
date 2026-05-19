package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.GroupBuyTeamStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Repository
public class RedisGroupBuyTeamStockRepository implements GroupBuyTeamStockRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGroupBuyTeamStockRepository.class);
    private static final Duration DEFAULT_STOCK_TTL = Duration.ofHours(2);
    private static final Duration RECOVERY_LOCK_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisGroupBuyTeamStockRepository(StringRedisTemplate redisTemplate,
                                            @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
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
            long recoveryCount = longValue(redisTemplate.opsForValue().get(recoveryKey));
            Long stockCount = redisTemplate.opsForValue().increment(stockKey);
            long occupy = (stockCount == null ? 0L : stockCount) + 1L;
            redisTemplate.expire(stockKey, ttl);
            redisTemplate.expire(recoveryKey, ttl);
            if (occupy > targetCount + recoveryCount) {
                redisTemplate.opsForValue().decrement(stockKey);
                return false;
            }
            String lockKey = stockKey + ":" + occupy;
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
            return Boolean.TRUE.equals(locked);
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
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", RECOVERY_LOCK_TTL);
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            String recoveryKey = recoveryTeamStockKey(activityId, teamId);
            redisTemplate.opsForValue().increment(recoveryKey);
            redisTemplate.expire(recoveryKey, stockTtl(validEndTime));
        } catch (Exception e) {
            try {
                redisTemplate.delete(lockKey);
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

    private long longValue(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
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
