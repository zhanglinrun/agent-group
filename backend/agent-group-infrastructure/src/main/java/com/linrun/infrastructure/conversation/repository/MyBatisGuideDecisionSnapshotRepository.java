package com.linrun.infrastructure.conversation.repository;

import com.linrun.domain.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.conversation.model.GuideDecisionSnapshot;
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
