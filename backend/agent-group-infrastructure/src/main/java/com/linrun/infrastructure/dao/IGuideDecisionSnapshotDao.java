package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GuideDecisionSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGuideDecisionSnapshotDao {

    void insert(GuideDecisionSnapshotPO snapshot);

    GuideDecisionSnapshotPO queryByDecisionId(@Param("decisionId") String decisionId);
}
