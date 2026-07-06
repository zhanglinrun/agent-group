package com.linrun.infrastructure.quota.repository;

import com.linrun.domain.quota.adapter.QuotaOrderSnapshotRepository;
import com.linrun.domain.quota.model.QuotaOrderSnapshot;
import com.linrun.infrastructure.dao.IQuotaOrderSnapshotDao;
import com.linrun.infrastructure.quota.converter.QuotaPOConverter;
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
            quotaOrderSnapshotDao.insert(QuotaPOConverter.toPO(snapshot));
        }
    }

    @Override
    public Optional<QuotaOrderSnapshot> queryByDecisionId(String decisionId) {
        return Optional.ofNullable(QuotaPOConverter.toEntity(quotaOrderSnapshotDao.queryByDecisionId(decisionId)));
    }
}














