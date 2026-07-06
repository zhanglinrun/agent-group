package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyActivityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGroupBuyActivityDao {

    GroupBuyActivityPO queryByGoodsId(@Param("goodsId") String goodsId);

    GroupBuyActivityPO queryByActivityId(@Param("activityId") String activityId);

    List<GroupBuyActivityPO> queryActivityList(@Param("limit") int limit);

    int insertActivity(GroupBuyActivityPO activity);

    int updateActivity(GroupBuyActivityPO activity);

    int updateEnabled(@Param("activityId") String activityId, @Param("enabled") Boolean enabled);

    int deleteByActivityId(@Param("activityId") String activityId);
}















