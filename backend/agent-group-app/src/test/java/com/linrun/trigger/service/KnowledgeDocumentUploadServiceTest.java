package com.linrun.trigger.service;

import com.linrun.api.knowledge.request.UploadKnowledgeDocumentRequest;
import com.linrun.api.knowledge.response.UploadKnowledgeDocumentResponse;
import com.linrun.domain.knowledge.adapter.KnowledgeEmbeddingClient;
import com.linrun.domain.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledge.model.KnowledgeDocument;
import com.linrun.domain.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.domain.knowledge.service.KnowledgeDocumentParser;
import com.linrun.domain.knowledge.service.KnowledgeDocumentService;
import com.linrun.domain.knowledge.service.KnowledgeVectorService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentUploadServiceTest {

    @Test
    void shouldUploadTextAndPersistFragments() {
        FakeKnowledgeDocumentRepository repository = new FakeKnowledgeDocumentRepository();
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeDocumentUploadService service = service(repository, vectorRepository);

        UploadKnowledgeDocumentResponse response = service.uploadText(request());

        assertTrue(response.getDocumentId().startsWith("DOC"));
        assertEquals("学习平板售后政策", response.getDocumentName());
        assertEquals("售后政策", response.getDocumentType());
        assertEquals("v3", response.getKnowledgeVersion());
        assertEquals("OPERATOR_UPLOAD", response.getSourceType());
        assertEquals("after-sale.md", response.getSourceName());
        assertEquals(KnowledgeDocumentStatus.ENABLED.name(), response.getDocumentStatus());
        assertEquals(2, response.getFragmentCount());
        assertNotNull(response.getCreateTime());
        assertEquals(2, response.getFragments().size());
        assertEquals(KnowledgeFragmentStatus.ENABLED.name(), response.getFragments().get(0).getFragmentStatus());
        assertEquals(response.getDocumentId(), repository.document.getDocumentId());
        assertEquals(2, repository.fragments.size());
        assertEquals(2, vectorRepository.savedFragments.size());
    }

    @Test
    void shouldUseDefaultVersionAndSourceWhenBlank() {
        FakeKnowledgeDocumentRepository repository = new FakeKnowledgeDocumentRepository();
        KnowledgeDocumentUploadService service = service(repository);
        UploadKnowledgeDocumentRequest request = request();
        request.setKnowledgeVersion(" ");
        request.setSourceType(null);
        request.setSourceName("");

        UploadKnowledgeDocumentResponse response = service.uploadText(request);

        assertEquals("v1", response.getKnowledgeVersion());
        assertEquals("OPERATOR_UPLOAD", response.getSourceType());
        assertEquals("学习平板售后政策", response.getSourceName());
    }

    @Test
    void shouldRejectBlankContent() {
        KnowledgeDocumentUploadService service = service(new FakeKnowledgeDocumentRepository());
        UploadKnowledgeDocumentRequest request = request();
        request.setContent(" ");

        AppException exception = assertThrows(AppException.class, () -> service.uploadText(request));

        assertEquals("0001", exception.getCode());
        assertEquals("content cannot be blank", exception.getMessage());
    }

    private KnowledgeDocumentUploadService service(FakeKnowledgeDocumentRepository repository) {
        return service(repository, new FakeKnowledgeVectorRepository());
    }

    private KnowledgeDocumentUploadService service(FakeKnowledgeDocumentRepository repository,
                                                   FakeKnowledgeVectorRepository vectorRepository) {
        return new KnowledgeDocumentUploadService(
                new KnowledgeDocumentService(),
                new KnowledgeDocumentParser(),
                repository,
                new KnowledgeVectorService(new FakeKnowledgeEmbeddingClient(), vectorRepository));
    }

    private UploadKnowledgeDocumentRequest request() {
        UploadKnowledgeDocumentRequest request = new UploadKnowledgeDocumentRequest();
        request.setDocumentName("学习平板售后政策");
        request.setDocumentType("售后政策");
        request.setKnowledgeVersion("v3");
        request.setSourceType("OPERATOR_UPLOAD");
        request.setSourceName("after-sale.md");
        request.setGoodsId("G10001");
        request.setContent("支持 7 天无理由退货。\n\n未成团订单自动退款。");
        return request;
    }

    private static class FakeKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

        private KnowledgeDocument document;
        private final List<KnowledgeFragment> fragments = new ArrayList<>();

        @Override
        public void save(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
            this.document = document;
            this.fragments.clear();
            this.fragments.addAll(fragments);
        }

        @Override
        public Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId) {
            return Optional.ofNullable(document)
                    .filter(item -> item.getDocumentId().equals(documentId));
        }

        @Override
        public List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId) {
            return fragments.stream()
                    .filter(item -> item.getDocumentId().equals(documentId))
                    .toList();
        }
    }

    private static class FakeKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

        @Override
        public List<Double> embed(String content) {
            return List.of(1.0d, 0.0d, 0.0d);
        }
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> savedFragments = new ArrayList<>();

        @Override
        public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
            savedFragments.add(fragment);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
            return savedFragments.stream()
                    .limit(limit)
                    .toList();
        }
    }
}
