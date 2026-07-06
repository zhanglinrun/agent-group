package com.linrun.trigger.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多模态与文本模型并存时，默认注入 openAiChatModel，避免 ChatModel 歧义。
 */
@Configuration
public class PrimaryChatModelConfig {

    @Bean
    @Primary
    @ConditionalOnBean(name = "openAiChatModel")
    public ChatModel primaryChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
