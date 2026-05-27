package com.linrun.infrastructure.dao;

import com.linrun.domain.activity.model.GroupBuyOrderLock;
import com.linrun.domain.activity.model.GroupBuyTeam;
import com.linrun.domain.activity.model.GroupBuyTeamDetail;
import com.linrun.domain.activity.model.GroupBuyTeamStatistic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    int releaseLockedOrder(@Param("orderId") String orderId);

    int releasePaidOrder(@Param("orderId") String orderId);

    int reduceTeamLockCount(@Param("teamId") String teamId);

    int reduceTeamPaidCount(@Param("teamId") String teamId);

    List<String> queryTimeoutUnsettledPaidOrderIds(@Param("deadline") LocalDateTime deadline,
                                                   @Param("limit") int limit);

    int countUserActivityLocks(@Param("userId") String userId, @Param("activityId") String activityId);

    List<GroupBuyTeamDetail> queryInProgressOwnerTeamDetails(@Param("activityId") String activityId,
                                                             @Param("userId") String userId,
                                                             @Param("limit") int limit);

    List<GroupBuyTeamDetail> queryInProgressRandomTeamDetails(@Param("activityId") String activityId,
                                                              @Param("userId") String userId,
                                                              @Param("limit") int limit);

    GroupBuyTeamStatistic queryTeamStatisticByActivityId(@Param("activityId") String activityId);
}
