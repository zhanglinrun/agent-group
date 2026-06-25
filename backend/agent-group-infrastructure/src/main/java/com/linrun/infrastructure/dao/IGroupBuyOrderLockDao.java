package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyOrderLockPO;
import com.linrun.infrastructure.po.GroupBuyTeamDetailPO;
import com.linrun.infrastructure.po.GroupBuyTeamPO;
import com.linrun.infrastructure.po.GroupBuyTeamStatisticPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IGroupBuyOrderLockDao {

    void insertTeam(GroupBuyTeamPO team);

    int updateTeamLockCount(@Param("teamId") String teamId);

    void insertOrderLock(GroupBuyOrderLockPO orderLock);

    GroupBuyOrderLockPO queryLockByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    GroupBuyOrderLockPO queryLockByOrderId(@Param("orderId") String orderId);

    GroupBuyTeamPO queryTeamByTeamId(@Param("teamId") String teamId);

    int updateOrderLockPaid(@Param("orderId") String orderId);

    int updateTeamCompleteCount(@Param("teamId") String teamId);

    List<String> queryPaidOrderIdsByTeamId(@Param("teamId") String teamId);

    int releaseLockedOrder(@Param("orderId") String orderId);

    int releasePaidOrder(@Param("orderId") String orderId);

    int reduceTeamLockCount(@Param("teamId") String teamId);

    int reduceTeamPaidCount(@Param("teamId") String teamId);

    List<String> queryTimeoutUnsettledPaidOrderIds(@Param("deadline") LocalDateTime deadline,
                                                   @Param("limit") int limit);

    List<String> queryTimeoutUnsettledLockedOrderIds(@Param("deadline") LocalDateTime deadline,
                                                     @Param("limit") int limit);

    int countUserActivityLocks(@Param("userId") String userId, @Param("activityId") String activityId);

    int countInProgressLocksByActivityId(@Param("activityId") String activityId);

    List<GroupBuyTeamDetailPO> queryInProgressOwnerTeamDetails(@Param("activityId") String activityId,
                                                               @Param("userId") String userId,
                                                               @Param("limit") int limit);

    List<GroupBuyTeamDetailPO> queryInProgressRandomTeamDetails(@Param("activityId") String activityId,
                                                                @Param("userId") String userId,
                                                                @Param("limit") int limit);

    GroupBuyTeamStatisticPO queryTeamStatisticByActivityId(@Param("activityId") String activityId);
}















