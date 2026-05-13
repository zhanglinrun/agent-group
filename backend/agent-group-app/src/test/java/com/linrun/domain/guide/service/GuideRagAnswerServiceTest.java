package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideRagAnswerServiceTest {

    @Test
    void shouldUseLlmAnswerWhenReturned() {
        GuideLlmClient llmClient = prompt -> "第一段\n第二段";
        GuideRagAnswerService service = new GuideRagAnswerService(
                new GuideRagPromptBuilder(GuideRagPromptBuilderTest.promptTemplateService()),
                llmClient);

        List<String> segments = service.answer("拼团失败会退款吗", GuideRagPromptBuilderTest.decisionResult());

        assertEquals(List.of("第一段", "第二段"), segments);
    }

    @Test
    void shouldFallbackWhenLlmAnswerIsBlank() {
        GuideLlmClient llmClient = prompt -> " ";
        GuideRagAnswerService service = new GuideRagAnswerService(
                new GuideRagPromptBuilder(GuideRagPromptBuilderTest.promptTemplateService()),
                llmClient);

        List<String> segments = service.answer("拼团失败会退款吗", GuideRagPromptBuilderTest.decisionResult());

        assertEquals(4, segments.size());
        assertEquals("我先结合商品资料、拼团试算和知识片段给你结论。", segments.get(0));
    }
}
