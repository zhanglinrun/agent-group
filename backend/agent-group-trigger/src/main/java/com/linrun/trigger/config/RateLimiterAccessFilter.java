package com.linrun.trigger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimiterAccessFilter extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/v1/agent/stream",
            "/api/v1/market/trade/lock",
            "/api/v1/market/trade/refund",
            "/api/v1/trade/payment/webhook/**",
            "/api/v1/trade/payment/refund",
            "/api/v1/market/trade/lock_market_pay_order",
            "/api/v1/market/trade/refund_market_pay_order"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final boolean enabled;
    private final int capacity;
    private final int refillTokens;
    private final long refillNanos;

    public RateLimiterAccessFilter(RedissonClient redissonClient,
                                   @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix,
                                   @Value("${agent.group.access-limit.enabled:true}") boolean enabled,
                                   @Value("${agent.group.access-limit.capacity:60}") int capacity,
                                   @Value("${agent.group.access-limit.refill-tokens:60}") int refillTokens,
                                   @Value("${agent.group.access-limit.refill-seconds:60}") int refillSeconds) {
        this.redissonClient = redissonClient;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
        this.enabled = enabled;
        this.capacity = Math.max(1, capacity);
        this.refillTokens = Math.max(1, refillTokens);
        this.refillNanos = Duration.ofSeconds(Math.max(1, refillSeconds)).toNanos();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return PROTECTED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = clientKey(request);
        if (!tryAcquire(key)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"操作过于频繁，请稍后再试\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String key) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimiterKey(key));
            rateLimiter.trySetRate(RateType.OVERALL, refillTokens, Math.max(1, refillNanos / 1_000_000_000L),
                    RateIntervalUnit.SECONDS);
            return rateLimiter.tryAcquire();
        } catch (Exception ignored) {
            TokenBucket bucket = buckets.computeIfAbsent(key, unused -> new TokenBucket(capacity, refillTokens, refillNanos));
            return bucket.tryAcquire();
        }
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = StringUtils.hasText(forwardedFor)
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return clientIp + "|" + request.getMethod() + "|" + request.getRequestURI();
    }

    private String rateLimiterKey(String key) {
        return keyPrefix + ":access:rate-limit:" + key.replaceAll("[^a-zA-Z0-9:_|.-]", "_");
    }

    private static class TokenBucket {

        private final int capacity;
        private final int refillTokens;
        private final long refillNanos;
        private int tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, int refillTokens, long refillNanos) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
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
            long elapsed = now - lastRefillNanos;
            if (elapsed < refillNanos) {
                return;
            }
            long rounds = elapsed / refillNanos;
            long refilled = rounds * refillTokens;
            tokens = (int) Math.min(capacity, tokens + refilled);
            lastRefillNanos += rounds * refillNanos;
        }
    }
}















