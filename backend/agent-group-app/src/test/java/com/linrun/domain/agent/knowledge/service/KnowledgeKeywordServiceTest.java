package com.linrun.domain.agent.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeKeywordServiceTest {

    @Test
    void shouldExtractBusinessKeywordsFirst() {
        KnowledgeKeywordService service = new KnowledgeKeywordService();

        List<String> keywords = service.extractKeywords("我想了解拼团失败后退款和售后怎么办");

        assertTrue(keywords.contains("拼团"));
        assertTrue(keywords.contains("退款"));
        assertTrue(keywords.contains("售后"));
    }

    @Test
    void shouldReturnEmptyWhenQuestionIsBlank() {
        KnowledgeKeywordService service = new KnowledgeKeywordService();

        assertEquals(List.of(), service.extractKeywords(" "));
    }
}
