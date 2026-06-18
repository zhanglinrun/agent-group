package com.linrun.infrastructure.trade.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 支付网关调用的熔断降级与重试配置（Resilience4j）。
 * <p>
 * 支付宝/微信支付是资金链路上的外部依赖，可能限流、超时或不可用：
 * <ul>
 *   <li>熔断 {@code paymentGatewayCircuitBreaker}：失败率/慢调用率超阈值 OPEN，快速失败并降级，
 *       避免外部网关故障把支付主链路拖垮；等待半开探测恢复。</li>
 *   <li>重试 {@code paymentGatewayRetry}：仅用于可重试操作（创建支付/查询），按指数退避重试瞬时失败。</li>
 * </ul>
 * 退款等写操作不自动重试，避免重复退款风险（由调用方幂等控制）。
 */
@Configuration
public class PaymentResilienceConfig {

    @Bean
    public CircuitBreaker paymentGatewayCircuitBreaker(
            @Value("${agent.group.payment.circuitbreaker.failure-rate-threshold:0.5}") float failureRateThreshold,
            @Value("${agent.group.payment.circuitbreaker.slow-call-rate-threshold:0.8}") float slowCallRateThreshold,
            @Value("${agent.group.payment.circuitbreaker.wait-duration-seconds:60}") long waitDurationSeconds,
            @Value("${agent.group.payment.circuitbreaker.slow-call-duration-seconds:15}") long slowCallDurationSeconds,
            @Value("${agent.group.payment.circuitbreaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${agent.group.payment.circuitbreaker.minimum-number-of-calls:6}") int minimumNumberOfCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationSeconds))
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationSeconds))
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreaker.of("paymentGateway", config);
    }

    @Bean
    public Retry paymentGatewayRetry(
            @Value("${agent.group.payment.retry.max-attempts:3}") int maxAttempts,
            @Value("${agent.group.payment.retry.initial-interval-seconds:1}") long initialIntervalSeconds) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofSeconds(initialIntervalSeconds), 2.0d))
                .build();
        return Retry.of("paymentGateway", config);
    }
}
