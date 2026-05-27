package com.linrun.infrastructure.payment;

import com.linrun.domain.payment.adapter.PaymentWebhookReplayRepository;
import com.linrun.types.exception.AppException;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisPaymentWebhookReplayRepository implements PaymentWebhookReplayRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPaymentWebhookReplayRepository.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    public RedisPaymentWebhookReplayRepository(RedissonClient redissonClient,
                                               @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public boolean acquireProcessingLock(String replayKey, Duration ttl) {
        if (!StringUtils.hasText(replayKey)) {
            return false;
        }
        try {
            Duration safeTtl = safeTtl(ttl);
            return redissonClient.<String>getBucket(key(replayKey))
                    .trySet("1", safeTtl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.error("payment webhook replay redis unavailable, replayKey={}, reason={}",
                    replayKey, e.getClass().getSimpleName());
            throw new AppException("PAY_0015", "支付回调防重放缓存不可用");
        }
    }

    @Override
    public void releaseProcessingLock(String replayKey) {
        if (!StringUtils.hasText(replayKey)) {
            return;
        }
        try {
            redissonClient.getBucket(key(replayKey)).delete();
        } catch (Exception e) {
            LOGGER.warn("payment webhook replay redis release failed, replayKey={}, reason={}",
                    replayKey, e.getClass().getSimpleName());
        }
    }

    private Duration safeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return DEFAULT_TTL;
        }
        return ttl;
    }

    private String key(String replayKey) {
        return keyPrefix + ":payment:webhook:replay:" + replayKey;
    }
}
