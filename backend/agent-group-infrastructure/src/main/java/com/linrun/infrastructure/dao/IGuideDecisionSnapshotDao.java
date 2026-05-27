package com.linrun.infrastructure.dao;

import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGuideDecisionSnapshotDao {

    void insert(GuideDecisionSnapshot snapshot);

    GuideDecisionSnapshot queryByDecisionId(@Param("decisionId") String decisionId);
}
