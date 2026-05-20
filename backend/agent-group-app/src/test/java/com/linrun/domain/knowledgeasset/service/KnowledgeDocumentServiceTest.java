package com.linrun.domain.knowledgeasset.service;

import com.linrun.domain.knowledgeasset.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.knowledgeasset.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.knowledgeasset.model.KnowledgeDocument;
import com.linrun.domain.knowledgeasset.model.KnowledgeDocumentBuildResult;
import com.linrun.domain.knowledgeasset.model.KnowledgeDocumentStatus;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragmentStatus;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentServiceTest {

    @Test
    void shouldCreateEnabledDocumentAndFragments() {
        KnowledgeDocumentService service = new KnowledgeDocumentService();

        KnowledgeDocumentBuildResult result = service.createParsedDocument(documentCommand(), List.of(
                fragmentCommand("G10001", "标准版适合写论文和看网课。", 1),
                fragmentCommand("G10001", "拼团价比原价低 300 元。", 2)));

        KnowledgeDocument document = result.getDocument();
        assertTrue(document.getDocumentId().startsWith("DOC"));
        assertEquals("学习平板商品详情", document.getDocumentName());
        assertEquals("商品详情", document.getDocumentType());
        assertEquals("v2", document.getKnowledgeVersion());
        assertEquals("OPERATOR_UPLOAD", document.getSourceType());
        assertEquals("admin-product-detail.md", document.getSourceName());
        assertEquals(KnowledgeDocumentStatus.ENABLED, document.getDocumentStatus());
        assertTrue(document.getEnabled());
        assertNotNull(document.getCreateTime());

        List<KnowledgeFragment> fragments = result.getFragments();
        assertEquals(2, fragments.size());
        assertTrue(fragments.get(0).getFragmentId().startsWith("KF"));
        assertEquals(document.getDocumentId(), fragments.get(0).getDocumentId());
        assertEquals(document.getDocumentType(), fragments.get(0).getDocumentType());
        assertEquals(document.getKnowledgeVersion(), fragments.get(0).getKnowledgeVersion());
        assertEquals(KnowledgeFragmentStatus.ENABLED, fragments.get(0).getFragmentStatus());
        assertTrue(fragments.get(0).getEnabled());
        assertEquals(1, fragments.get(0).getRankNo());
    }

    @Test
    void shouldRejectBlankDocumentName() {
        KnowledgeDocumentService service = new KnowledgeDocumentService();
        CreateKnowledgeDocumentCommand command = documentCommand();
        command.setDocumentName(" ");

        AppException exception = assertThrows(AppException.class,
                () -> service.createParsedDocument(command, List.of(fragmentCommand("G10001", "内容", 1))));

        assertEquals("0001", exception.getCode());
        assertEquals("documentName cannot be blank", exception.getMessage());
    }

    @Test
    void shouldRejectEmptyFragments() {
        KnowledgeDocumentService service = new KnowledgeDocumentService();

        AppException exception = assertThrows(AppException.class,
                () -> service.createParsedDocument(documentCommand(), List.of()));

        assertEquals("0001", exception.getCode());
        assertEquals("knowledge fragments cannot be empty", exception.getMessage());
    }

    @Test
    void shouldDisableDocumentAndFragment() {
        KnowledgeDocumentService service = new KnowledgeDocumentService();
        KnowledgeDocumentBuildResult result = service.createParsedDocument(documentCommand(),
                List.of(fragmentCommand("G10001", "内容", 1)));

        result.getDocument().disable();
        result.getFragments().get(0).disable();

        assertEquals(KnowledgeDocumentStatus.DISABLED, result.getDocument().getDocumentStatus());
        assertFalse(result.getDocument().getEnabled());
        assertEquals(KnowledgeFragmentStatus.DISABLED, result.getFragments().get(0).getFragmentStatus());
        assertFalse(result.getFragments().get(0).getEnabled());
    }

    private CreateKnowledgeDocumentCommand documentCommand() {
        CreateKnowledgeDocumentCommand command = new CreateKnowledgeDocumentCommand();
        command.setDocumentName("学习平板商品详情");
        command.setDocumentType("商品详情");
        command.setKnowledgeVersion("v2");
        command.setSourceType("OPERATOR_UPLOAD");
        command.setSourceName("admin-product-detail.md");
        return command;
    }

    private CreateKnowledgeFragmentCommand fragmentCommand(String goodsId, String content, int rankNo) {
        CreateKnowledgeFragmentCommand command = new CreateKnowledgeFragmentCommand();
        command.setGoodsId(goodsId);
        command.setContent(content);
        command.setRankNo(rankNo);
        return command;
    }
}
