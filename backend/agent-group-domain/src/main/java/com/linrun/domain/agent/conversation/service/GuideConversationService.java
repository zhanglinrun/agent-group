package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import com.linrun.domain.agent.conversation.model.GuideMessageRole;
import com.linrun.domain.agent.conversation.model.GuideUserInput;
import com.linrun.domain.support.config.service.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuideConversationService {

    private static final int RECENT_MESSAGE_LIMIT = 6;
    private static final int MAX_CONTEXT_CHARS = 1600;

    private final GuideConversationRepository guideConversationRepository;
    private final GuideContextCompactor guideContextCompactor;
    private final DynamicConfigService dynamicConfigService;

    public GuideConversationService(GuideConversationRepository guideConversationRepository) {
        this(guideConversationRepository, new GuideContextCompactor(), null);
    }

    @Autowired
    public GuideConversationService(GuideConversationRepository guideConversationRepository,
                                    GuideContextCompactor guideContextCompactor,
                                    DynamicConfigService dynamicConfigService) {
        this.guideConversationRepository = guideConversationRepository;
        this.guideContextCompactor = guideContextCompactor == null ? new GuideContextCompactor() : guideContextCompactor;
        this.dynamicConfigService = dynamicConfigService;
    }

    public String buildQuestionWithContext(GuideUserInput input) {
        String question = StringUtils.hasText(input.getQuestion())
                ? input.getQuestion().trim()
                : "请根据图片帮我判断商品是否适合购买，并给出导购建议";
        String currentTurn = question;
        if (StringUtils.hasText(input.getImageSummary())) {
            currentTurn = currentTurn + "\n本轮图片线索：" + input.getImageSummary();
        }

        List<GuideConversationMessage> recentMessages = guideConversationRepository
                .queryRecentMessages(input.getSessionId(), RECENT_MESSAGE_LIMIT);
        if (recentMessages.isEmpty()) {
            return currentTurn;
        }

        return """
                最近对话：
                %s

                本轮问题：
                %s
                """.formatted(conversationContext(recentMessages), currentTurn);
    }

    public void rememberUserInput(GuideUserInput input) {
        String content = StringUtils.hasText(input.getQuestion())
                ? input.getQuestion().trim()
                : "用户上传图片并请求导购建议";
        if (StringUtils.hasText(input.getImageSummary())) {
            content = content + "\n图片线索：" + input.getImageSummary();
        }
        guideConversationRepository.appendMessage(input.getSessionId(),
                GuideConversationMessage.user(content, input.getImageUrl()));
    }

    public void rememberAssistantAnswer(String sessionId, List<String> answerSegments) {
        if (!StringUtils.hasText(sessionId) || answerSegments == null || answerSegments.isEmpty()) {
            return;
        }
        String content = answerSegments.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (StringUtils.hasText(content)) {
            guideConversationRepository.appendMessage(sessionId, GuideConversationMessage.assistant(content));
        }
    }

    private String conversationContext(List<GuideConversationMessage> recentMessages) {
        List<String> lines = recentMessages.stream()
                .map(this::messageLine)
                .toList();
        return guideContextCompactor.compact(lines, maxContextChars());
    }

    private int maxContextChars() {
        return dynamicConfigService == null
                ? MAX_CONTEXT_CHARS
                : dynamicConfigService.agentContextCompactThreshold();
    }

    private String messageLine(GuideConversationMessage message) {
        String role = GuideMessageRole.USER.equals(message.getRole()) ? "用户" : "导购";
        return role + "：" + message.getContent();
    }
}
