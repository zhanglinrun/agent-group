package com.linrun.trigger.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
public class RateLimiterAccessAspect {

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final Map<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> localRejectCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> localBlacklistUntil = new ConcurrentHashMap<>();

    public RateLimiterAccessAspect(RedissonClient redissonClient,
                                   @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
    }

    @Around("@annotation(rateLimiter)")
    public Object doRouter(ProceedingJoinPoint joinPoint, RateLimiterAccessInterceptor rateLimiter) throws Throwable {
        String limitKey = buildLimitKey(joinPoint, rateLimiter);
        if (isBlacklisted(limitKey, rateLimiter) || !tryAcquire(limitKey, rateLimiter)) {
            return invokeFallback(joinPoint, rateLimiter);
        }
        resetRejectCount(limitKey);
        return joinPoint.proceed();
    }

    private boolean tryAcquire(String limitKey, RateLimiterAccessInterceptor rateLimiter) {
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(redisKey("access:method:rate:" + limitKey));
            limiter.trySetRate(RateType.OVERALL, Math.max(1L, Math.round(rateLimiter.permitsPerSecond())),
                    1, RateIntervalUnit.SECONDS);
            boolean acquired = limiter.tryAcquire();
            if (!acquired) {
                increaseRejectCount(limitKey, rateLimiter);
            }
            return acquired;
        } catch (Exception ignored) {
            TokenBucket bucket = localBuckets.computeIfAbsent(limitKey,
                    key -> new TokenBucket(Math.max(1, (int) Math.ceil(rateLimiter.permitsPerSecond())), Duration.ofSeconds(1).toNanos()));
            boolean acquired = bucket.tryAcquire();
            if (!acquired) {
                increaseLocalRejectCount(limitKey, rateLimiter);
            }
            return acquired;
        }
    }

    private boolean isBlacklisted(String limitKey, RateLimiterAccessInterceptor rateLimiter) {
        try {
            RBucket<Boolean> bucket = redissonClient.getBucket(redisKey("access:method:blacklist:" + limitKey));
            return Boolean.TRUE.equals(bucket.get());
        } catch (Exception ignored) {
            Long until = localBlacklistUntil.get(limitKey);
            if (until == null) {
                return false;
            }
            if (until <= System.currentTimeMillis()) {
                localBlacklistUntil.remove(limitKey);
                return false;
            }
            return true;
        }
    }

    private void increaseRejectCount(String limitKey, RateLimiterAccessInterceptor rateLimiter) {
        if (rateLimiter.blacklistCount() <= 0) {
            return;
        }
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(redisKey("access:method:reject:" + limitKey));
            long count = counter.incrementAndGet();
            counter.expire(Duration.ofSeconds(rateLimiter.blacklistSeconds()));
            if (count >= rateLimiter.blacklistCount()) {
                redissonClient.<Boolean>getBucket(redisKey("access:method:blacklist:" + limitKey))
                        .set(true, Duration.ofSeconds(rateLimiter.blacklistSeconds()));
            }
        } catch (Exception ignored) {
            increaseLocalRejectCount(limitKey, rateLimiter);
        }
    }

    private void increaseLocalRejectCount(String limitKey, RateLimiterAccessInterceptor rateLimiter) {
        if (rateLimiter.blacklistCount() <= 0) {
            return;
        }
        int count = localRejectCounts.computeIfAbsent(limitKey, key -> new AtomicInteger()).incrementAndGet();
        if (count >= rateLimiter.blacklistCount()) {
            localBlacklistUntil.put(limitKey, System.currentTimeMillis() + Duration.ofSeconds(rateLimiter.blacklistSeconds()).toMillis());
        }
    }

    private void resetRejectCount(String limitKey) {
        localRejectCounts.remove(limitKey);
    }

    private Object invokeFallback(ProceedingJoinPoint joinPoint, RateLimiterAccessInterceptor rateLimiter) throws Throwable {
        if (!StringUtils.hasText(rateLimiter.fallbackMethod())) {
            return joinPoint.proceed();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method fallback = ReflectionUtils.findMethod(joinPoint.getTarget().getClass(),
                rateLimiter.fallbackMethod(), signature.getMethod().getParameterTypes());
        if (fallback == null) {
            fallback = ReflectionUtils.findMethod(joinPoint.getTarget().getClass(), rateLimiter.fallbackMethod());
        }
        if (fallback == null) {
            return joinPoint.proceed();
        }
        ReflectionUtils.makeAccessible(fallback);
        return fallback.getParameterCount() == 0
                ? fallback.invoke(joinPoint.getTarget())
                : fallback.invoke(joinPoint.getTarget(), joinPoint.getArgs());
    }

    private String buildLimitKey(ProceedingJoinPoint joinPoint, RateLimiterAccessInterceptor rateLimiter) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String keyValue = resolveKeyValue(rateLimiter.key(), joinPoint.getArgs());
        String methodKey = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        return sanitize(methodKey + ":" + keyValue);
    }

    private String resolveKeyValue(String key, Object[] args) {
        if (!StringUtils.hasText(key)) {
            return "global";
        }
        String propertyName = key.startsWith("#") ? key.substring(1) : key;
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof Map<?, ?> map && map.containsKey(propertyName)) {
                return String.valueOf(map.get(propertyName));
            }
            if (arg instanceof String value && isTokenKey(propertyName) && StringUtils.hasText(value)) {
                return value;
            }
            BeanWrapper wrapper = new BeanWrapperImpl(arg);
            if (wrapper.isReadableProperty(propertyName)) {
                Object value = wrapper.getPropertyValue(propertyName);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        return "global";
    }

    private boolean isTokenKey(String propertyName) {
        return "token".equalsIgnoreCase(propertyName)
                || "authorization".equalsIgnoreCase(propertyName);
    }

    private String redisKey(String key) {
        return keyPrefix + ":" + key;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9:_|.-]", "_");
    }

    private static class TokenBucket {

        private final int capacity;
        private final long refillNanos;
        private int tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, long refillNanos) {
            this.capacity = capacity;
            this.refillNanos = refillNanos;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens <= 0) {
                return false;
            }
            tokens--;
            return true;
        }

        private void refill() {
            long now = System.nanoTime();
            if (now - lastRefillNanos < refillNanos) {
                return;
            }
            tokens = capacity;
            lastRefillNanos = now;
        }
    }
}















