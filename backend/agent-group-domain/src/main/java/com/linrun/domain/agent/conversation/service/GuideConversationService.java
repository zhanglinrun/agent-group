package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideConversationRepository;
import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import com.linrun.domain.agent.conversation.model.GuideMessageRole;
import com.linrun.domain.agent.conversation.model.GuideUserInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuideConversationService {

    private static final int RECENT_MESSAGE_LIMIT = 6;
    private static final int MAX_CONTEXT_CHARS = 1600;
    private static final String CONTEXT_COMPACT_MARK = "[older conversation compacted]";

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
        List<String> lines = recentMessages.stream()
                .map(this::messageLine)
                .toList();
        String context = String.join("\n", lines);
        if (context.length() <= MAX_CONTEXT_CHARS) {
            return context;
        }
        return compactLines(lines);
    }

    private String compactLines(List<String> lines) {
        List<String> keptLines = new ArrayList<>();
        int currentLength = CONTEXT_COMPACT_MARK.length();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            int nextLength = currentLength + line.length() + 1;
            if (nextLength > MAX_CONTEXT_CHARS) {
                break;
            }
            keptLines.add(0, line);
            currentLength = nextLength;
        }
        if (keptLines.isEmpty() && !lines.isEmpty()) {
            String lastLine = lines.get(lines.size() - 1);
            int keepLength = Math.max(0, MAX_CONTEXT_CHARS - CONTEXT_COMPACT_MARK.length() - 1);
            keptLines.add(lastLine.substring(Math.max(0, lastLine.length() - keepLength)));
        }
        return CONTEXT_COMPACT_MARK + "\n" + String.join("\n", keptLines);
    }

    private String messageLine(GuideConversationMessage message) {
        String role = GuideMessageRole.USER.equals(message.getRole()) ? "用户" : "导购";
        return role + "：" + message.getContent();
    }
}
