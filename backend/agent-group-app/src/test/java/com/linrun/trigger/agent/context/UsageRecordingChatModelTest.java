package com.linrun.trigger.agent.context;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UsageRecordingChatModel 熔断降级测试")
class UsageRecordingChatModelTest {

    /** 构造一个很容易触发 OPEN 的熔断器：4 次调用窗口、4 次起算、失败率阈值 50%。 */
    private CircuitBreaker fastOpenBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        return CircuitBreaker.of("test-llm", config);
    }

    @Test
    @DisplayName("正常调用应委托底层模型并原样返回响应")
    void shouldDelegateCallWhenHealthy() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        when(delegate.call(any(Prompt.class))).thenReturn(expected);

        UsageRecordingChatModel model = new UsageRecordingChatModel(delegate, "conv-1", fastOpenBreaker(), null);

        ChatResponse actual = model.call(new Prompt("q"));
        assertThat(actual).isSameAs(expected);
        verify(delegate, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("连续失败打满滑动窗口后熔断器 OPEN，后续调用被快速拒绝且不再请求底层模型")
    void shouldOpenCircuitAfterFailures() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class))).thenThrow(new RuntimeException("llm down"));
        UsageRecordingChatModel model = new UsageRecordingChatModel(delegate, "conv-2", fastOpenBreaker(), null);

        // 4 次失败耗尽滑动窗口（minimumNumberOfCalls=4），失败率 100% > 50%，熔断器转为 OPEN
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> model.call(new Prompt("q")))
                .isInstanceOf(RuntimeException.class);
        }

        // 熔断 OPEN：直接抛 CallNotPermittedException，不再调用底层模型
        assertThatThrownBy(() -> model.call(new Prompt("q")))
            .isInstanceOf(CallNotPermittedException.class);
        // 底层模型只被调用 4 次，第 5 次被熔断器拦截
        verify(delegate, times(4)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("熔断器/重试器为 null 时退回原始调用，保持兼容")
    void shouldFallbackWhenNoResilience() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        when(delegate.call(any(Prompt.class))).thenReturn(expected);

        UsageRecordingChatModel model = new UsageRecordingChatModel(delegate, "conv-3");

        assertThat(model.call(new Prompt("q"))).isSameAs(expected);
        verify(delegate, times(1)).call(any(Prompt.class));
    }
}
