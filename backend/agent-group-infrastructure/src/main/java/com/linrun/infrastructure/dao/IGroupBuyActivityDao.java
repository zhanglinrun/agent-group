package com.linrun.infrastructure.dao;

import com.linrun.domain.activity.model.GroupBuyActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyActivityDao {

    GroupBuyActivity queryByGoodsId(@Param("goodsId") String goodsId);

    GroupBuyActivity queryByActivityId(@Param("activityId") String activityId);
}
