package com.linrun.domain.groupbuy.adapter.repository;

import com.linrun.domain.groupbuy.model.GroupBuyActivity;

import java.util.List;
import java.util.Optional;

public interface GroupBuyActivityRepository {

    Optional<GroupBuyActivity> queryByGoodsId(String goodsId);

    Optional<GroupBuyActivity> queryByActivityId(String activityId);

    default List<GroupBuyActivity> queryActivityList(int limit) {
        return List.of();
    }
}















