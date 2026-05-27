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
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedissonService {

    private final RedissonClient redissonClient;

    public RedissonService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    public <T> void setValue(String key, T value, Duration ttl) {
        redissonClient.<T>getBucket(key).set(value, ttl);
    }

    public <T> boolean trySet(String key, T value, Duration ttl) {
        return redissonClient.<T>getBucket(key).trySet(value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    public boolean exists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    public boolean remove(String key) {
        return redissonClient.getBucket(key).delete();
    }

    public RAtomicLong getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key);
    }

    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    public long decr(String key) {
        return redissonClient.getAtomicLong(key).decrementAndGet();
    }

    public <T> RBucket<T> getBucket(String key) {
        return redissonClient.getBucket(key);
    }

    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> blockingQueue) {
        return redissonClient.getDelayedQueue(blockingQueue);
    }

    public <T> RList<T> getList(String key) {
        return redissonClient.getList(key);
    }

    public <T> RSet<T> getSet(String key) {
        return redissonClient.getSet(key);
    }

    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    public RTopic getTopic(String key) {
        return redissonClient.getTopic(key);
    }

    public RRateLimiter getRateLimiter(String key) {
        return redissonClient.getRateLimiter(key);
    }

    public boolean setNx(String key, Duration ttl) {
        return redissonClient.getBucket(key).trySet("1", ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return redissonClient.getLock(key).tryLock(waitTime, leaseTime, unit);
    }
}
