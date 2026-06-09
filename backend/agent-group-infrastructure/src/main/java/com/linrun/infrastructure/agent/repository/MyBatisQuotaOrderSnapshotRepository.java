package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.conversation.adapter.QuotaOrderSnapshotRepository;
import com.linrun.domain.agent.conversation.model.QuotaOrderSnapshot;
import com.linrun.infrastructure.agent.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IQuotaOrderSnapshotDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisQuotaOrderSnapshotRepository implements QuotaOrderSnapshotRepository {

    private final IQuotaOrderSnapshotDao QuotaOrderSnapshotDao;

    public MyBatisQuotaOrderSnapshotRepository(IQuotaOrderSnapshotDao QuotaOrderSnapshotDao) {
        this.QuotaOrderSnapshotDao = QuotaOrderSnapshotDao;
    }

    @Override
    public void save(QuotaOrderSnapshot snapshot) {
        if (snapshot != null) {
            QuotaOrderSnapshotDao.insert(AgentPOConverter.toPO(snapshot));
        }
    }

    @Override
    public Optional<QuotaOrderSnapshot> queryByDecisionId(String decisionId) {
        return Optional.ofNullable(AgentPOConverter.toEntity(QuotaOrderSnapshotDao.queryByDecisionId(decisionId)));
    }
}















