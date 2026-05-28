package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyStockFlowPO;
import com.linrun.infrastructure.po.GroupBuyStockPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyStockDao {

    GroupBuyStockPO queryByActivityId(@Param("activityId") String activityId);

    GroupBuyStockPO queryByActivityIdAndGoodsIdForUpdate(@Param("activityId") String activityId,
                                                         @Param("goodsId") String goodsId);

    int lockStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int markPaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releaseLockedStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releasePaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    void insertStockFlow(GroupBuyStockFlowPO flow);
}
