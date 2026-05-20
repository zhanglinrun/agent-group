package com.linrun.domain.knowledgeasset.service;

import com.linrun.domain.knowledgeasset.model.CreateKnowledgeFragmentCommand;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentParserTest {

    @Test
    void shouldSplitContentByParagraph() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        List<CreateKnowledgeFragmentCommand> fragments = parser.parse(
                "G10001",
                "第一段商品详情。\n\n第二段售后政策。");

        assertEquals(2, fragments.size());
        assertEquals("G10001", fragments.get(0).getGoodsId());
        assertEquals("第一段商品详情。", fragments.get(0).getContent());
        assertEquals(1, fragments.get(0).getRankNo());
        assertEquals("第二段售后政策。", fragments.get(1).getContent());
        assertEquals(2, fragments.get(1).getRankNo());
    }

    @Test
    void shouldSplitLongContent() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();
        String content = "a".repeat(1100);

        List<CreateKnowledgeFragmentCommand> fragments = parser.parse("G10001", content);

        assertEquals(3, fragments.size());
        assertTrue(fragments.stream().allMatch(fragment -> fragment.getContent().length() <= 500));
    }

    @Test
    void shouldRejectBlankContent() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        AppException exception = assertThrows(AppException.class, () -> parser.parse("G10001", " "));

        assertEquals("0001", exception.getCode());
        assertEquals("content cannot be blank", exception.getMessage());
    }
}
