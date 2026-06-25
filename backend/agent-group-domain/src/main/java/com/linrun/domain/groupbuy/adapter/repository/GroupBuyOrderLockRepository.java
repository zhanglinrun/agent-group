package com.linrun.domain.groupbuy.adapter.repository;

import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.groupbuy.model.GroupBuyTeamDetail;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatistic;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

public interface GroupBuyOrderLockRepository {

    Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey);

    Optional<GroupBuyTeam> queryTeamByTeamId(String teamId);

    GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock);

    GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock);

    Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId);

    GroupBuySettlementResult settlePaidOrder(String orderId);

    List<String> queryPaidOrderIdsByTeamId(String teamId);

    GroupBuySettlementResult releaseLockedOrder(String orderId);

    GroupBuySettlementResult releasePaidOrder(String orderId);

    default List<String> queryTimeoutUnsettledPaidOrderIds(LocalDateTime deadline, int limit) {
        return List.of();
    }

    default List<String> queryTimeoutUnsettledLockedOrderIds(LocalDateTime deadline, int limit) {
        return List.of();
    }

    default int countUserActivityLocks(String userId, String activityId) {
        return 0;
    }

    /**
     * 统计活动下处于进行中（LOCKED 或 PAID）的锁单数，用于删除活动前的校验。
     */
    default int countInProgressLocksByActivityId(String activityId) {
        return 0;
    }

    default List<GroupBuyTeamDetail> queryInProgressTeamDetails(String activityId,
                                                                String userId,
                                                                int ownerCount,
                                                                int randomCount) {
        return List.of();
    }

    default GroupBuyTeamStatistic queryTeamStatisticByActivityId(String activityId) {
        return new GroupBuyTeamStatistic();
    }

    static GroupBuyOrderLockRepository noop() {
        return new GroupBuyOrderLockRepository() {
            @Override
            public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
                return Optional.empty();
            }

            @Override
            public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
                return Optional.empty();
            }

            @Override
            public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
                return new GroupBuyLockResult(orderLock, team, true);
            }

            @Override
            public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
                return new GroupBuyLockResult(orderLock, null, true);
            }

            @Override
            public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
                return Optional.empty();
            }

            @Override
            public GroupBuySettlementResult settlePaidOrder(String orderId) {
                return new GroupBuySettlementResult();
            }

            @Override
            public List<String> queryPaidOrderIdsByTeamId(String teamId) {
                return List.of();
            }

            @Override
            public GroupBuySettlementResult releaseLockedOrder(String orderId) {
                return new GroupBuySettlementResult();
            }

            @Override
            public GroupBuySettlementResult releasePaidOrder(String orderId) {
                return new GroupBuySettlementResult();
            }
        };
    }
}















