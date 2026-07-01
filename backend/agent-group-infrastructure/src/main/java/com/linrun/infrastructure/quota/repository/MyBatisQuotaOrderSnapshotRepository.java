package com.linrun.infrastructure.quota.repository;

import com.linrun.domain.quota.adapter.QuotaOrderSnapshotRepository;
import com.linrun.domain.quota.model.QuotaOrderSnapshot;
import com.linrun.infrastructure.agent.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IQuotaOrderSnapshotDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisQuotaOrderSnapshotRepository implements QuotaOrderSnapshotRepository {

    private final IQuotaOrderSnapshotDao quotaOrderSnapshotDao;

    public MyBatisQuotaOrderSnapshotRepository(IQuotaOrderSnapshotDao quotaOrderSnapshotDao) {
        this.quotaOrderSnapshotDao = quotaOrderSnapshotDao;
    }

    @Override
    public void save(QuotaOrderSnapshot snapshot) {
        if (snapshot != null) {
            quotaOrderSnapshotDao.insert(AgentPOConverter.toPO(snapshot));
        }
    }

    @Override
    public Optional<QuotaOrderSnapshot> queryByDecisionId(String decisionId) {
        return Optional.ofNullable(AgentPOConverter.toEntity(quotaOrderSnapshotDao.queryByDecisionId(decisionId)));
    }
}














