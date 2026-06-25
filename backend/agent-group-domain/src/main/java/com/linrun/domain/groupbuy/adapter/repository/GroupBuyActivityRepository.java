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

    /**
     * 保存活动（新建），返回持久化后的活动（含生成的 activityId）。
     */
    default GroupBuyActivity save(GroupBuyActivity activity) {
        return activity;
    }

    /**
     * 更新活动基本信息，返回更新后的活动。
     */
    default GroupBuyActivity update(GroupBuyActivity activity) {
        return activity;
    }

    /**
     * 更新活动启用状态（上下架）。
     */
    default boolean updateEnabled(String activityId, boolean enabled) {
        return false;
    }

    /**
     * 删除活动，返回是否删除成功。
     */
    default boolean removeByActivityId(String activityId) {
        return false;
    }
}
















