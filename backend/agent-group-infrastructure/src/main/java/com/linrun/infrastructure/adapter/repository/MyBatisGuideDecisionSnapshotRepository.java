package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;
import com.linrun.infrastructure.dao.IGuideDecisionSnapshotDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisGuideDecisionSnapshotRepository implements GuideDecisionSnapshotRepository {

    private final IGuideDecisionSnapshotDao guideDecisionSnapshotDao;

    public MyBatisGuideDecisionSnapshotRepository(IGuideDecisionSnapshotDao guideDecisionSnapshotDao) {
        this.guideDecisionSnapshotDao = guideDecisionSnapshotDao;
    }

    @Override
    public void save(GuideDecisionSnapshot snapshot) {
        if (snapshot != null) {
            guideDecisionSnapshotDao.insert(snapshot);
        }
    }

    @Override
    public Optional<GuideDecisionSnapshot> queryByDecisionId(String decisionId) {
        return Optional.ofNullable(guideDecisionSnapshotDao.queryByDecisionId(decisionId));
    }
}
