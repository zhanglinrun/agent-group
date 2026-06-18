package com.linrun.trigger.agent.checkpoint;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Agent 断点快照存储：基于 {@link RedissonClient} 的轻量封装。
 *
 * <p>trigger 层直接持有 RedissonClient（不走 infrastructure 的 RedissonService），
 * 避免跨层依赖。每个 continueTraceId 对应一个快照桶（key = agent:checkpoint:{continueTraceId}），
 * 默认 TTL 24 小时（可用 agent.checkpoint.ttl-hours 调整）。所有读写都吞异常降级，
 * 保证快照机制故障不会拖垮主执行链路。
 */
@Slf4j
@Component
public class AgentCheckpointStore {

    private static final String KEY_PREFIX = "agent:checkpoint:";

    private final RedissonClient redissonClient;
    private final AgentCheckpointSerializer serializer;
    private final Duration ttl;

    public AgentCheckpointStore(RedissonClient redissonClient,
                                @Value("${agent.checkpoint.ttl-hours:24}") long ttlHours) {
        this.redissonClient = redissonClient;
        this.serializer = new AgentCheckpointSerializer();
        this.ttl = Duration.ofHours(Math.max(1L, ttlHours));
    }

    /** 保存（覆盖）断点快照。savedAt 由本方法自动盖时间戳。 */
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || !StringUtils.hasText(checkpoint.getContinueTraceId())) {
            return;
        }
        checkpoint.setSavedAt(System.currentTimeMillis());
        try {
            RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + checkpoint.getContinueTraceId());
            bucket.set(serializer.toJson(checkpoint), ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("checkpoint 保存失败，已降级跳过：traceId={}, reason={}",
                    checkpoint.getContinueTraceId(), e.getMessage());
        }
    }

    /** 读取断点快照；不存在或解析失败时返回 empty，绝不抛异常打断主链路。 */
    public Optional<AgentCheckpoint> load(String continueTraceId) {
        if (!StringUtils.hasText(continueTraceId)) {
            return Optional.empty();
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + continueTraceId);
            String json = bucket.get();
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(serializer.fromJson(json));
        } catch (Exception e) {
            log.warn("checkpoint 读取失败，按无快照处理：traceId={}, reason={}",
                    continueTraceId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 任务正常完成后清理快照，避免无谓占用。 */
    public void clear(String continueTraceId) {
        if (!StringUtils.hasText(continueTraceId)) {
            return;
        }
        try {
            redissonClient.getBucket(KEY_PREFIX + continueTraceId).delete();
        } catch (Exception e) {
            log.warn("checkpoint 清理失败，忽略：traceId={}, reason={}",
                    continueTraceId, e.getMessage());
        }
    }
}
