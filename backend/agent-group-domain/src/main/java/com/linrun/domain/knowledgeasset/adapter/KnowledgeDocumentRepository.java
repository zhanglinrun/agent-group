package com.linrun.domain.knowledgeasset.adapter;

import com.linrun.domain.knowledgeasset.model.KnowledgeDocument;
import com.linrun.domain.knowledgeasset.model.KnowledgeDocumentStatus;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository {

    void save(KnowledgeDocument document, List<KnowledgeFragment> fragments);

    Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId);

    List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId);

    List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion);

    default List<KnowledgeDocument> queryDocumentsByStatus(KnowledgeDocumentStatus status, int limit) {
        return List.of();
    }

    default void updateDocumentStatus(KnowledgeDocument document) {
    }
}
