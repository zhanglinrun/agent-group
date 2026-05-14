package com.linrun.domain.knowledge.adapter;

import com.linrun.domain.knowledge.model.KnowledgeDocument;
import com.linrun.domain.knowledge.model.KnowledgeFragment;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository {

    void save(KnowledgeDocument document, List<KnowledgeFragment> fragments);

    Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId);

    List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId);

    List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion);
}
