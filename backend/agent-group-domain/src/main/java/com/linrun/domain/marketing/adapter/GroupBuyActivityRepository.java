package com.linrun.domain.marketing.adapter;

import com.linrun.domain.marketing.model.GroupBuyActivity;

import java.util.Optional;

public interface GroupBuyActivityRepository {

    Optional<GroupBuyActivity> queryByGoodsId(String goodsId);

    Optional<GroupBuyActivity> queryByActivityId(String activityId);
}
