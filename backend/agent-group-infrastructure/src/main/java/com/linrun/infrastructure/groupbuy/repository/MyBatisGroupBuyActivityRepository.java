package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.infrastructure.groupbuy.converter.ActivityPOConverter;
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
}















