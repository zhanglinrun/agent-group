package com.linrun.domain.agent.knowledge.service;

import com.linrun.domain.agent.knowledge.model.CreateKnowledgeFragmentCommand;
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
                "第一段额度说明。\n\n第二段退款规则。");

        assertEquals(2, fragments.size());
        assertEquals("G10001", fragments.get(0).getGoodsId());
        assertEquals("第一段额度说明。", fragments.get(0).getContent());
        assertEquals(1, fragments.get(0).getRankNo());
        assertEquals("第二段退款规则。", fragments.get(1).getContent());
        assertEquals(2, fragments.get(1).getRankNo());
    }

    @Test
    void shouldSplitLongContent() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();
        String content = "a".repeat(1100);

        List<CreateKnowledgeFragmentCommand> fragments = parser.parse("G10001", content);

        assertEquals(4, fragments.size());
        assertEquals("PARENT", fragments.get(0).getChunkType());
        assertEquals(Boolean.FALSE, fragments.get(0).getEmbeddingEnabled());
        assertTrue(fragments.stream()
                .filter(fragment -> "CHILD".equals(fragment.getChunkType()))
                .allMatch(fragment -> fragment.getContent().length() <= 500));
        assertTrue(fragments.stream()
                .filter(fragment -> "CHILD".equals(fragment.getChunkType()))
                .allMatch(fragment -> fragments.get(0).getParentKey().equals(fragment.getParentKey())));
    }

    @Test
    void shouldSplitMarkdownByHeading() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        List<CreateKnowledgeFragmentCommand> fragments = parser.parse(
                "G10001",
                "# 额度说明\n适合学术问答\n## 退款规则\n支持未成团退款");

        assertEquals(2, fragments.size());
        assertTrue(fragments.get(0).getContent().startsWith("# 额度说明"));
        assertTrue(fragments.get(1).getContent().startsWith("## 退款规则"));
    }

    @Test
    void shouldRejectBlankContent() {
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        AppException exception = assertThrows(AppException.class, () -> parser.parse("G10001", " "));

        assertEquals("0001", exception.getCode());
        assertEquals("content cannot be blank", exception.getMessage());
    }
}















