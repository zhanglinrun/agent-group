package com.linrun.domain.agent.conversation.adapter;

import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;

import java.util.Optional;

public interface GuideDecisionSnapshotRepository {

    void save(GuideDecisionSnapshot snapshot);

    Optional<GuideDecisionSnapshot> queryByDecisionId(String decisionId);

    static GuideDecisionSnapshotRepository noop() {
        return new GuideDecisionSnapshotRepository() {
            @Override
            public void save(GuideDecisionSnapshot snapshot) {
            }

            @Override
            public Optional<GuideDecisionSnapshot> queryByDecisionId(String decisionId) {
                return Optional.empty();
            }
        };
    }
}
