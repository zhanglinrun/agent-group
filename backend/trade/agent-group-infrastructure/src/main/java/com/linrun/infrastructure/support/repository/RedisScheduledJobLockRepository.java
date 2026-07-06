package com.linrun.infrastructure.support.repository;

import com.linrun.domain.support.adapter.ScheduledJobLockRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisScheduledJobLockRepository implements ScheduledJobLockRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisScheduledJobLockRepository.class);
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    public RedisScheduledJobLockRepository(RedissonClient redissonClient,
                                           @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Optional<String> tryLock(String lockName, Duration leaseTime) {
        if (!StringUtils.hasText(lockName)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        try {
            RLock lock = redissonClient.getLock(key(lockName));
            boolean locked = lock.tryLock(0L, safeLeaseTime(leaseTime).toMillis(), TimeUnit.MILLISECONDS);
            return locked ? Optional.of(token) : Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("scheduled job redis lock unavailable, lockName={}, reason={}",
                    lockName, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void unlock(String lockName, String lockToken) {
        if (!StringUtils.hasText(lockName) || !StringUtils.hasText(lockToken)) {
            return;
        }
        try {
            RLock lock = redissonClient.getLock(key(lockName));
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            LOGGER.warn("scheduled job redis unlock failed, lockName={}, reason={}",
                    lockName, e.getClass().getSimpleName());
        }
    }

    private Duration safeLeaseTime(Duration leaseTime) {
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            return DEFAULT_LEASE_TIME;
        }
        return leaseTime;
    }

    private String key(String lockName) {
        return keyPrefix + ":job:lock:" + lockName;
    }
}















