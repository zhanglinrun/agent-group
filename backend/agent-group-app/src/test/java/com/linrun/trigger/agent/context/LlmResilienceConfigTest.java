package com.linrun.trigger.agent.context;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmResilienceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LlmResilienceConfig.class);

    @Test
    void defaultsUsePercentageThresholds() {
        contextRunner.run(context -> {
            CircuitBreaker circuitBreaker = context.getBean("llmChatCircuitBreaker", CircuitBreaker.class);

            assertEquals(50f, circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
            assertEquals(80f, circuitBreaker.getCircuitBreakerConfig().getSlowCallRateThreshold());
        });
    }
}
