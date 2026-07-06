package com.linrun.infrastructure.support.redis;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBucket;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 统一服务接口。
 *
 * 对齐 group 项目 IRedisService 的设计：上层依赖该接口而非具体实现，
 * 便于替换底层 Redis 客户端或在测试中注入假实现。RedissonService 是其默认实现。
 */
public interface IRedisService {

    <T> void setValue(String key, T value);

    <T> void setValue(String key, T value, Duration ttl);

    <T> boolean trySet(String key, T value, Duration ttl);

    <T> T getValue(String key);

    boolean exists(String key);

    boolean remove(String key);

    RAtomicLong getAtomicLong(String key);

    long incr(String key);

    long decr(String key);

    <T> RBucket<T> getBucket(String key);

    <T> RQueue<T> getQueue(String key);

    <T> RBlockingQueue<T> getBlockingQueue(String key);

    <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> blockingQueue);

    <T> RList<T> getList(String key);

    <T> RSet<T> getSet(String key);

    <K, V> RMap<K, V> getMap(String key);

    RLock getLock(String key);

    RTopic getTopic(String key);

    RRateLimiter getRateLimiter(String key);

    boolean setNx(String key, Duration ttl);

    boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
}
