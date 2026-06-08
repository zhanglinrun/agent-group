package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.infrastructure.agent.converter.AgentPOConverter;
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
        knowledgeDocumentDao.insertDocument(AgentPOConverter.toPO(document));
        if (fragments != null && !fragments.isEmpty()) {
            knowledgeDocumentDao.insertFragments(AgentPOConverter.toKnowledgeFragmentPOList(fragments));
        }
    }

    @Override
    public Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId) {
        return Optional.ofNullable(AgentPOConverter.toEntity(knowledgeDocumentDao.queryDocumentByDocumentId(documentId)));
    }

    @Override
    public List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId) {
        return AgentPOConverter.toKnowledgeFragments(knowledgeDocumentDao.queryFragmentsByDocumentId(documentId));
    }

    @Override
    public List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion) {
        return AgentPOConverter.toKnowledgeFragments(knowledgeDocumentDao.queryEnabledFragmentsByVersion(knowledgeVersion));
    }

    @Override
    public List<KnowledgeDocument> queryDocumentsByStatus(KnowledgeDocumentStatus status, int limit) {
        return AgentPOConverter.toKnowledgeDocuments(
                knowledgeDocumentDao.queryDocumentsByStatus(status == null ? null : status.name(), Math.max(1, limit)));
    }

    @Override
    public void updateDocumentStatus(KnowledgeDocument document) {
        knowledgeDocumentDao.updateDocumentStatus(AgentPOConverter.toPO(document));
    }

    @Override
    public void updateFragmentsStatusByDocumentId(String documentId,
                                                  KnowledgeFragmentStatus status,
                                                  boolean enabled) {
        knowledgeDocumentDao.updateFragmentsStatusByDocumentId(
                documentId,
                status == null ? null : status.name(),
                enabled);
    }
}
