package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.conversation.model.GuideConversationMessage;
import com.linrun.domain.conversation.model.GuideMessageRole;
import com.linrun.domain.conversation.model.GuideUserInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuideConversationService {

    private static final int RECENT_MESSAGE_LIMIT = 6;

    private final GuideConversationRepository guideConversationRepository;

    public GuideConversationService(GuideConversationRepository guideConversationRepository) {
        this.guideConversationRepository = guideConversationRepository;
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
        return recentMessages.stream()
                .map(this::messageLine)
                .collect(Collectors.joining("\n"));
    }

    private String messageLine(GuideConversationMessage message) {
        String role = GuideMessageRole.USER.equals(message.getRole()) ? "用户" : "导购";
        return role + "：" + message.getContent();
    }
}
