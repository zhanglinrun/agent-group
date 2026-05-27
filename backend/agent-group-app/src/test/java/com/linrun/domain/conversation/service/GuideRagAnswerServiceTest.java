package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRagAnswerServiceTest {

    @Test
    void shouldUseLlmAnswerWhenReturned() {
        GuideLlmClient llmClient = prompt -> "第一段\n第二段";
        GuideRagAnswerService service = new GuideRagAnswerService(
                new GuideRagPromptBuilder(GuideRagPromptBuilderTest.promptTemplateService()),
                llmClient);

        List<String> segments = service.answer("拼团失败会退款吗", GuideRagPromptBuilderTest.decisionResult());

        assertEquals("第一段", segments.get(0));
        assertEquals("第二段", segments.get(1));
        assertTrue(segments.stream().anyMatch(segment -> segment.contains("工具结果校验")));
        assertTrue(segments.stream().anyMatch(segment -> segment.contains("拼团价 2099.00")));
    }

    @Test
    void shouldFallbackWhenLlmAnswerIsBlank() {
        GuideLlmClient llmClient = prompt -> " ";
        GuideRagAnswerService service = new GuideRagAnswerService(
                new GuideRagPromptBuilder(GuideRagPromptBuilderTest.promptTemplateService()),
                llmClient);

        List<String> segments = service.answer("拼团失败会退款吗", GuideRagPromptBuilderTest.decisionResult());

        assertEquals("我先结合商品资料、拼团试算和知识片段给你结论。", segments.get(0));
        assertTrue(segments.stream().anyMatch(segment -> segment.contains("当前拼团价是 2099.00")));
    }
}
