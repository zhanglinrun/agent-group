package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.QuotaOrderSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IQuotaOrderSnapshotDao {

    void insert(QuotaOrderSnapshotPO snapshot);

    QuotaOrderSnapshotPO queryByDecisionId(@Param("decisionId") String decisionId);
}















