package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.infrastructure.market.converter.ActivityPOConverter;
import com.linrun.infrastructure.dao.IGroupBuyActivityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGroupBuyActivityRepository implements GroupBuyActivityRepository {

    private final IGroupBuyActivityDao groupBuyActivityDao;

    public MyBatisGroupBuyActivityRepository(IGroupBuyActivityDao groupBuyActivityDao) {
        this.groupBuyActivityDao = groupBuyActivityDao;
    }

    @Override
    public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyActivityDao.queryByGoodsId(goodsId)));
    }

    @Override
    public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyActivityDao.queryByActivityId(activityId)));
    }

    @Override
    public List<GroupBuyActivity> queryActivityList(int limit) {
        return ActivityPOConverter.toActivities(groupBuyActivityDao.queryActivityList(Math.max(1, limit)));
    }

    @Override
    public GroupBuyActivity save(GroupBuyActivity activity) {
        groupBuyActivityDao.insertActivity(ActivityPOConverter.toPO(activity));
        return queryByActivityId(activity.getActivityId()).orElse(activity);
    }

    @Override
    public GroupBuyActivity update(GroupBuyActivity activity) {
        groupBuyActivityDao.updateActivity(ActivityPOConverter.toPO(activity));
        return queryByActivityId(activity.getActivityId()).orElse(activity);
    }

    @Override
    public boolean updateEnabled(String activityId, boolean enabled) {
        return groupBuyActivityDao.updateEnabled(activityId, enabled) > 0;
    }

    @Override
    public boolean removeByActivityId(String activityId) {
        return groupBuyActivityDao.deleteByActivityId(activityId) > 0;
    }
}















