package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.infrastructure.dao.IGroupBuyOrderLockDao;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}
