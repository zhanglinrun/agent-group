package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideConversationRepository;
import com.linrun.domain.guide.model.GuideConversationMessage;
import com.linrun.domain.guide.model.GuideUserInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideConversationServiceTest {

    @Test
    void shouldBuildQuestionWithRecentConversationAndImageSummary() {
        FakeGuideConversationRepository repository = new FakeGuideConversationRepository();
        repository.appendMessage("S10001", GuideConversationMessage.user("我是学生，预算有限", ""));
        repository.appendMessage("S10001", GuideConversationMessage.assistant("建议优先看标准版"));
        GuideConversationService service = new GuideConversationService(repository);
        GuideUserInput input = new GuideUserInput();
        input.setSessionId("S10001");
        input.setQuestion("那拼团失败能退吗");
        input.setImageSummary("图片疑似平板商品或商品截图");

        String question = service.buildQuestionWithContext(input);

        assertTrue(question.contains("最近对话"));
        assertTrue(question.contains("用户：我是学生，预算有限"));
        assertTrue(question.contains("导购：建议优先看标准版"));
        assertTrue(question.contains("本轮问题"));
        assertTrue(question.contains("本轮图片线索：图片疑似平板商品或商品截图"));
    }

    @Test
    void shouldRememberUserInputAndAssistantAnswer() {
        FakeGuideConversationRepository repository = new FakeGuideConversationRepository();
        GuideConversationService service = new GuideConversationService(repository);
        GuideUserInput input = new GuideUserInput();
        input.setSessionId("S10001");
        input.setQuestion("推荐一款学习平板");
        input.setImageUrl("local-image://pad.png");
        input.setImageSummary("图片疑似平板商品或商品截图");

        service.rememberUserInput(input);
        service.rememberAssistantAnswer("S10001", List.of("推荐标准版", "拼团价更低"));

        List<GuideConversationMessage> messages = repository.queryRecentMessages("S10001", 10);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).getContent().contains("图片线索"));
        assertEquals("local-image://pad.png", messages.get(0).getImageUrl());
        assertTrue(messages.get(1).getContent().contains("推荐标准版"));
    }

    private static class FakeGuideConversationRepository implements GuideConversationRepository {

        private final List<GuideConversationMessage> messages = new ArrayList<>();

        @Override
        public List<GuideConversationMessage> queryRecentMessages(String sessionId, int limit) {
            int fromIndex = Math.max(0, messages.size() - limit);
            return messages.subList(fromIndex, messages.size());
        }

        @Override
        public void appendMessage(String sessionId, GuideConversationMessage message) {
            messages.add(message);
        }
    }
}
