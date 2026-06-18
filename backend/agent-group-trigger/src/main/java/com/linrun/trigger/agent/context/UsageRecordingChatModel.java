package com.linrun.trigger.agent.context;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

/**
 * 包装真实 ChatModel，在调用前后记录 token 用量，并对外部 LLM 调用做熔断与重试保护。
 * <p>
 * 同步 {@code call}：重试在内、熔断在外——熔断器先判断是否放行，放行后由重试器处理瞬时失败；
 * 重试耗尽后的最终失败才计一次熔断失败，避免一次抖动多次扣减熔断窗口。
 * 流式 {@code stream}：每次订阅都在熔断器监控下发起（{@code Flux.defer} + {@link CircuitBreakerOperator}），
 * 不做重试（流式部分失败的语义不明确，交给上层处理）。
 * 熔断器/重试器为 null 时退回原始调用，保持既有行为兼容。
 */
public class UsageRecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final String conversationId;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public UsageRecordingChatModel(ChatModel delegate, String conversationId) {
        this(delegate, conversationId, null, null);
    }

    public UsageRecordingChatModel(ChatModel delegate, String conversationId,
                                   CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = delegate;
        this.conversationId = conversationId;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        AcademicAgentTokenUsageRecorder.beginCall(conversationId);
        Supplier<ChatResponse> call = () -> delegate.call(prompt);
        if (retry != null) {
            call = Retry.decorateSupplier(retry, call);
        }
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        }
        ChatResponse response = call.get();
        AcademicAgentTokenUsageRecorder.record(conversationId, response);
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> source = Flux.defer(() -> delegate.stream(prompt))
                .doOnSubscribe(ignored -> AcademicAgentTokenUsageRecorder.beginCall(conversationId))
                .doOnNext(response -> AcademicAgentTokenUsageRecorder.record(conversationId, response));
        if (circuitBreaker != null) {
            source = source.transform(CircuitBreakerOperator.of(circuitBreaker));
        }
        return source;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }
}
