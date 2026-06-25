package com.linrun.trigger.agent.context;

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
 * LLM 调用的熔断降级与重试配置（Resilience4j）。
 * <p>
 * 外部大模型 API（DashScope / OpenAI 兼容）是不可控依赖，可能限流、超时或不可用：
 * <ul>
 *   <li>熔断 {@code llmChatCircuitBreaker}：基于滑动窗口失败率/慢调用率，超阈值 OPEN 后快速失败，
 *       避免故障级联拖垮 Agent 主链路；等待 {@code wait-duration} 后进入 HALF_OPEN 探测恢复。</li>
 *   <li>重试 {@code llmChatRetry}：瞬时异常按指数退避重试，缓解网络抖动与偶发限流。</li>
 * </ul>
 * 参数全部可配、带默认值，便于本地与线上区分。
 */
@Configuration
public class LlmResilienceConfig {

    @Bean
    public CircuitBreaker llmChatCircuitBreaker(
            @Value("${agent.group.llm.circuitbreaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${agent.group.llm.circuitbreaker.slow-call-rate-threshold:80}") float slowCallRateThreshold,
            @Value("${agent.group.llm.circuitbreaker.wait-duration-seconds:30}") long waitDurationSeconds,
            @Value("${agent.group.llm.circuitbreaker.slow-call-duration-seconds:20}") long slowCallDurationSeconds,
            @Value("${agent.group.llm.circuitbreaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${agent.group.llm.circuitbreaker.minimum-number-of-calls:10}") int minimumNumberOfCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationSeconds))
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationSeconds))
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreaker.of("llmChat", config);
    }

    @Bean
    public Retry llmChatRetry(
            @Value("${agent.group.llm.retry.max-attempts:3}") int maxAttempts,
            @Value("${agent.group.llm.retry.initial-interval-seconds:1}") long initialIntervalSeconds) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofSeconds(initialIntervalSeconds), 2.0d))
                .build();
        return Retry.of("llmChat", config);
    }
}
