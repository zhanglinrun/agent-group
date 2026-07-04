package com.linrun.trigger.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultimodalChatModelConfig {

    @Bean("multimodalChatModel")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.openai.api-key:}') "
            + "&& !'${spring.ai.openai.api-key:}'.equalsIgnoreCase('not-configured')")
    public ChatModel multimodalChatModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${agent.group.llm.multimodal.base-url:${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}}") String baseUrl,
            @Value("${agent.group.llm.multimodal.model:qwen3-vl-plus}") String model,
            @Value("${agent.group.llm.multimodal.temperature:0.2}") double temperature) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(temperature)
                .model(model)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(new SimpleApiKey(apiKey))
                        .build())
                .defaultOptions(options)
                .build();
    }
}
