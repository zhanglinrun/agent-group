package com.linrun.infrastructure.marketing.repository;

import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.infrastructure.dao.IGroupBuyActivityDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisGroupBuyActivityRepository implements GroupBuyActivityRepository {

    private final IGroupBuyActivityDao groupBuyActivityDao;

    public MyBatisGroupBuyActivityRepository(IGroupBuyActivityDao groupBuyActivityDao) {
        this.groupBuyActivityDao = groupBuyActivityDao;
    }

    @Override
    public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
        return Optional.ofNullable(groupBuyActivityDao.queryByGoodsId(goodsId));
    }

    @Override
    public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
        return Optional.ofNullable(groupBuyActivityDao.queryByActivityId(activityId));
    }
}
