package com.linrun.infrastructure.market.bitmap;

import com.linrun.domain.market.tag.adapter.CrowdTagBitmapPort;
import com.linrun.infrastructure.dao.ICrowdTagDao;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class RedisCrowdTagBitmapAdapter implements CrowdTagBitmapPort {

    private final RedissonClient redissonClient;
    private final ICrowdTagDao crowdTagDao;
    private final String keyPrefix;

    public RedisCrowdTagBitmapAdapter(RedissonClient redissonClient,
                                      ICrowdTagDao crowdTagDao,
                                      @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.crowdTagDao = crowdTagDao;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Override
    public Optional<Long> queryUserNumericId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        Long numericId = crowdTagDao.queryUserNumericId(userId);
        if (numericId == null || numericId <= 0) {
            return Optional.empty();
        }
        return Optional.of(numericId);
    }

    @Override
    public Optional<Boolean> isUserInTag(String tagId, String userId) {
        if (!StringUtils.hasText(tagId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        try {
            Optional<Long> numericId = queryUserNumericId(userId);
            if (numericId.isEmpty()) {
                return Optional.empty();
            }
            RBitSet bitSet = redissonClient.getBitSet(bitmapKey(tagId));
            return Optional.of(bitSet.get(numericId.get()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void markUserInTag(String tagId, long userNumericId) {
        if (!StringUtils.hasText(tagId) || userNumericId <= 0) {
            return;
        }
        try {
            redissonClient.getBitSet(bitmapKey(tagId)).set(userNumericId, true);
        } catch (Exception ignored) {
            // Redis 不可用时由 DB 兜底
        }
    }

    @Override
    public int countTaggedUsers(String tagId) {
        if (!StringUtils.hasText(tagId)) {
            return -1;
        }
        try {
            return (int) redissonClient.getBitSet(bitmapKey(tagId)).cardinality();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String bitmapKey(String tagId) {
        return keyPrefix + ":crowd:tag:" + tagId;
    }
}
