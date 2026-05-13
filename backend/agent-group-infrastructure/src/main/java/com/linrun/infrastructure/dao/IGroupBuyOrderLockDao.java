package com.linrun.infrastructure.dao;

import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyOrderLockDao {

    void insertTeam(GroupBuyTeam team);

    int updateTeamLockCount(@Param("teamId") String teamId);

    void insertOrderLock(GroupBuyOrderLock orderLock);

    GroupBuyOrderLock queryLockByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    GroupBuyTeam queryTeamByTeamId(@Param("teamId") String teamId);
}
