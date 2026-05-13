package com.linrun.domain.groupbuy.adapter;

import com.linrun.domain.groupbuy.model.GroupBuyActivity;

import java.util.Optional;

public interface GroupBuyActivityRepository {

    Optional<GroupBuyActivity> queryByGoodsId(String goodsId);

    Optional<GroupBuyActivity> queryByActivityId(String activityId);
}
