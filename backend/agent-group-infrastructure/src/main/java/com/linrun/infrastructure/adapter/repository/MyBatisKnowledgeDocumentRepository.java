package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.infrastructure.dao.IKnowledgeDocumentDao;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

    private final IKnowledgeDocumentDao knowledgeDocumentDao;

    public MyBatisKnowledgeDocumentRepository(IKnowledgeDocumentDao knowledgeDocumentDao) {
        this.knowledgeDocumentDao = knowledgeDocumentDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
        knowledgeDocumentDao.insertDocument(document);
        if (fragments != null && !fragments.isEmpty()) {
            knowledgeDocumentDao.insertFragments(fragments);
        }
    }

    @Override
    public Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId) {
        return Optional.ofNullable(knowledgeDocumentDao.queryDocumentByDocumentId(documentId));
    }

    @Override
    public List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId) {
        return knowledgeDocumentDao.queryFragmentsByDocumentId(documentId);
    }

    @Override
    public List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion) {
        return knowledgeDocumentDao.queryEnabledFragmentsByVersion(knowledgeVersion);
    }

    @Override
    public List<KnowledgeDocument> queryDocumentsByStatus(KnowledgeDocumentStatus status, int limit) {
        return knowledgeDocumentDao.queryDocumentsByStatus(status, Math.max(1, limit));
    }

    @Override
    public void updateDocumentStatus(KnowledgeDocument document) {
        knowledgeDocumentDao.updateDocumentStatus(document);
    }
}
