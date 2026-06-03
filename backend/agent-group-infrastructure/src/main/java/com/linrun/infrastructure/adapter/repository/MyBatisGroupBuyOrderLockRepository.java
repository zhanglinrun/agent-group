package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.model.GroupBuyLockResult;
import com.linrun.domain.activity.model.GroupBuyLockStatus;
import com.linrun.domain.activity.model.GroupBuyOrderLock;
import com.linrun.domain.activity.model.GroupBuySettlementResult;
import com.linrun.domain.activity.model.GroupBuyTeam;
import com.linrun.domain.activity.model.GroupBuyTeamDetail;
import com.linrun.domain.activity.model.GroupBuyTeamStatistic;
import com.linrun.infrastructure.converter.ActivityPOConverter;
import com.linrun.infrastructure.dao.IGroupBuyOrderLockDao;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGroupBuyOrderLockRepository implements GroupBuyOrderLockRepository {

    private final IGroupBuyOrderLockDao groupBuyOrderLockDao;

    public MyBatisGroupBuyOrderLockRepository(IGroupBuyOrderLockDao groupBuyOrderLockDao) {
        this.groupBuyOrderLockDao = groupBuyOrderLockDao;
    }

    @Override
    public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByIdempotentKey(idempotentKey)));
    }

    @Override
    public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamByTeamId(teamId)));
    }

    @Override
    public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
        groupBuyOrderLockDao.insertTeam(ActivityPOConverter.toPO(team));
        groupBuyOrderLockDao.insertOrderLock(ActivityPOConverter.toPO(orderLock));
        return new GroupBuyLockResult(orderLock, team, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
        int updated = groupBuyOrderLockDao.updateTeamLockCount(orderLock.getTeamId());
        if (updated != 1) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
        groupBuyOrderLockDao.insertOrderLock(ActivityPOConverter.toPO(orderLock));
        GroupBuyTeam team = ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId()));
        return new GroupBuyLockResult(orderLock, team, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuySettlementResult settlePaidOrder(String orderId) {
        GroupBuyOrderLock orderLock = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId)))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));

        int updated = groupBuyOrderLockDao.updateOrderLockPaid(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.updateTeamCompleteCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.PAID);
        } else {
            orderLock = ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId));
        }

        GroupBuyTeam team = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId())))
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        return new GroupBuySettlementResult(orderLock, team, updated != 1);
    }

    @Override
    public List<String> queryPaidOrderIdsByTeamId(String teamId) {
        return groupBuyOrderLockDao.queryPaidOrderIdsByTeamId(teamId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuySettlementResult releaseLockedOrder(String orderId) {
        GroupBuyOrderLock orderLock = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId)))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
        int updated = groupBuyOrderLockDao.releaseLockedOrder(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.reduceTeamLockCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
        } else {
            orderLock = ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId));
        }
        GroupBuyTeam team = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId())))
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        return new GroupBuySettlementResult(orderLock, team, updated != 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuySettlementResult releasePaidOrder(String orderId) {
        GroupBuyOrderLock orderLock = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId)))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
        int updated = groupBuyOrderLockDao.releasePaidOrder(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.reduceTeamPaidCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
        } else {
            orderLock = ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryLockByOrderId(orderId));
        }
        GroupBuyTeam team = Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId())))
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        return new GroupBuySettlementResult(orderLock, team, updated != 1);
    }

    @Override
    public List<String> queryTimeoutUnsettledPaidOrderIds(LocalDateTime deadline, int limit) {
        return groupBuyOrderLockDao.queryTimeoutUnsettledPaidOrderIds(deadline, limit);
    }

    @Override
    public List<String> queryTimeoutUnsettledLockedOrderIds(LocalDateTime deadline, int limit) {
        return groupBuyOrderLockDao.queryTimeoutUnsettledLockedOrderIds(deadline, limit);
    }

    @Override
    public int countUserActivityLocks(String userId, String activityId) {
        return groupBuyOrderLockDao.countUserActivityLocks(userId, activityId);
    }

    @Override
    public List<GroupBuyTeamDetail> queryInProgressTeamDetails(String activityId,
                                                               String userId,
                                                               int ownerCount,
                                                               int randomCount) {
        List<GroupBuyTeamDetail> result = new ArrayList<>();
        if (ownerCount > 0) {
            result.addAll(ActivityPOConverter.toTeamDetails(
                    groupBuyOrderLockDao.queryInProgressOwnerTeamDetails(activityId, userId, ownerCount)));
        }
        if (randomCount > 0) {
            result.addAll(ActivityPOConverter.toTeamDetails(
                    groupBuyOrderLockDao.queryInProgressRandomTeamDetails(activityId, userId, randomCount)));
        }
        return result;
    }

    @Override
    public GroupBuyTeamStatistic queryTeamStatisticByActivityId(String activityId) {
        GroupBuyTeamStatistic statistic = ActivityPOConverter.toEntity(groupBuyOrderLockDao.queryTeamStatisticByActivityId(activityId));
        return statistic == null ? new GroupBuyTeamStatistic() : statistic;
    }
}
