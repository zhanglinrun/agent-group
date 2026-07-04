package com.linrun.infrastructure.agent.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagKeywordRetrieverTest {

    @Test
    void retrieveMatchesQuestionTermsInExtractedText() {
        String text = "Spring Boot 使用 Redis 做拼团活动缓存，失败时降级回源 MySQL。";
        var hits = RagKeywordRetriever.retrieve(text, "拼团 Redis 缓存");
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).content().contains("Redis"));
    }

    @Test
    void retrieveReturnsEmptyWhenNoTermMatch() {
        var hits = RagKeywordRetriever.retrieve("只有向量检索相关内容", "xyzabc");
        assertTrue(hits.isEmpty());
    }
}
