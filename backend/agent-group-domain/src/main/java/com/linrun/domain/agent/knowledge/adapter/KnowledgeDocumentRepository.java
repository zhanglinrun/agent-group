package com.linrun.domain.agent.knowledge.adapter;

import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;

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

    default void updateFragmentsStatusByDocumentId(String documentId,
                                                   KnowledgeFragmentStatus status,
                                                   boolean enabled) {
    }
}
