package com.linrun.infrastructure.dao;

import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGroupBuyOrderLockDao {

    void insertTeam(GroupBuyTeam team);

    int updateTeamLockCount(@Param("teamId") String teamId);

    void insertOrderLock(GroupBuyOrderLock orderLock);

    GroupBuyOrderLock queryLockByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    GroupBuyOrderLock queryLockByOrderId(@Param("orderId") String orderId);

    GroupBuyTeam queryTeamByTeamId(@Param("teamId") String teamId);

    int updateOrderLockPaid(@Param("orderId") String orderId);

    int updateTeamCompleteCount(@Param("teamId") String teamId);

    List<String> queryPaidOrderIdsByTeamId(@Param("teamId") String teamId);
}
