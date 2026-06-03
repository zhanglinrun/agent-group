package com.linrun.trigger.agent.context;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class UsageRecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final String conversationId;

    public UsageRecordingChatModel(ChatModel delegate, String conversationId) {
        this.delegate = delegate;
        this.conversationId = conversationId;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        BearDoctorTokenUsageRecorder.beginCall(conversationId);
        ChatResponse response = delegate.call(prompt);
        BearDoctorTokenUsageRecorder.record(conversationId, response);
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt)
                .doOnSubscribe(ignored -> BearDoctorTokenUsageRecorder.beginCall(conversationId))
                .doOnNext(response -> BearDoctorTokenUsageRecorder.record(conversationId, response));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }
}
