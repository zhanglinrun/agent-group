package com.linrun.domain.quota.adapter;

import com.linrun.domain.quota.model.QuotaOrderSnapshot;

import java.util.Optional;

public interface QuotaOrderSnapshotRepository {

    void save(QuotaOrderSnapshot snapshot);

    Optional<QuotaOrderSnapshot> queryByDecisionId(String decisionId);

    static QuotaOrderSnapshotRepository noop() {
        return new QuotaOrderSnapshotRepository() {
            @Override
            public void save(QuotaOrderSnapshot snapshot) {
            }

            @Override
            public Optional<QuotaOrderSnapshot> queryByDecisionId(String decisionId) {
                return Optional.empty();
            }
        };
    }
}















