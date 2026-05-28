package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyActivityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyActivityDao {

    GroupBuyActivityPO queryByGoodsId(@Param("goodsId") String goodsId);

    GroupBuyActivityPO queryByActivityId(@Param("activityId") String activityId);
}
