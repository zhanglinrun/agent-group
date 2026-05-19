package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyLockStatus;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.groupbuy.model.GroupBuyTeamDetail;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatistic;
import com.linrun.infrastructure.dao.IGroupBuyOrderLockDao;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MyBatisGroupBuyOrderLockRepository implements GroupBuyOrderLockRepository {

    private final IGroupBuyOrderLockDao groupBuyOrderLockDao;

    public MyBatisGroupBuyOrderLockRepository(IGroupBuyOrderLockDao groupBuyOrderLockDao) {
        this.groupBuyOrderLockDao = groupBuyOrderLockDao;
    }

    @Override
    public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
        return Optional.ofNullable(groupBuyOrderLockDao.queryLockByIdempotentKey(idempotentKey));
    }

    @Override
    public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
        return Optional.ofNullable(groupBuyOrderLockDao.queryTeamByTeamId(teamId));
    }

    @Override
    public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
        return Optional.ofNullable(groupBuyOrderLockDao.queryLockByOrderId(orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
        groupBuyOrderLockDao.insertTeam(team);
        groupBuyOrderLockDao.insertOrderLock(orderLock);
        return new GroupBuyLockResult(orderLock, team, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
        int updated = groupBuyOrderLockDao.updateTeamLockCount(orderLock.getTeamId());
        if (updated != 1) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
        groupBuyOrderLockDao.insertOrderLock(orderLock);
        GroupBuyTeam team = groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId());
        return new GroupBuyLockResult(orderLock, team, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuySettlementResult settlePaidOrder(String orderId) {
        GroupBuyOrderLock orderLock = Optional.ofNullable(groupBuyOrderLockDao.queryLockByOrderId(orderId))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));

        int updated = groupBuyOrderLockDao.updateOrderLockPaid(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.updateTeamCompleteCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.PAID);
        } else {
            orderLock = groupBuyOrderLockDao.queryLockByOrderId(orderId);
        }

        GroupBuyTeam team = Optional.ofNullable(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId()))
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
        GroupBuyOrderLock orderLock = Optional.ofNullable(groupBuyOrderLockDao.queryLockByOrderId(orderId))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
        int updated = groupBuyOrderLockDao.releaseLockedOrder(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.reduceTeamLockCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
        } else {
            orderLock = groupBuyOrderLockDao.queryLockByOrderId(orderId);
        }
        GroupBuyTeam team = Optional.ofNullable(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId()))
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        return new GroupBuySettlementResult(orderLock, team, updated != 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuySettlementResult releasePaidOrder(String orderId) {
        GroupBuyOrderLock orderLock = Optional.ofNullable(groupBuyOrderLockDao.queryLockByOrderId(orderId))
                .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
        int updated = groupBuyOrderLockDao.releasePaidOrder(orderId);
        if (updated == 1) {
            groupBuyOrderLockDao.reduceTeamPaidCount(orderLock.getTeamId());
            orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
        } else {
            orderLock = groupBuyOrderLockDao.queryLockByOrderId(orderId);
        }
        GroupBuyTeam team = Optional.ofNullable(groupBuyOrderLockDao.queryTeamByTeamId(orderLock.getTeamId()))
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        return new GroupBuySettlementResult(orderLock, team, updated != 1);
    }

    @Override
    public List<String> queryTimeoutUnsettledPaidOrderIds(LocalDateTime deadline, int limit) {
        return groupBuyOrderLockDao.queryTimeoutUnsettledPaidOrderIds(deadline, limit);
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
            result.addAll(groupBuyOrderLockDao.queryInProgressOwnerTeamDetails(activityId, userId, ownerCount));
        }
        if (randomCount > 0) {
            result.addAll(groupBuyOrderLockDao.queryInProgressRandomTeamDetails(activityId, userId, randomCount));
        }
        return result;
    }

    @Override
    public GroupBuyTeamStatistic queryTeamStatisticByActivityId(String activityId) {
        GroupBuyTeamStatistic statistic = groupBuyOrderLockDao.queryTeamStatisticByActivityId(activityId);
        return statistic == null ? new GroupBuyTeamStatistic() : statistic;
    }
}
