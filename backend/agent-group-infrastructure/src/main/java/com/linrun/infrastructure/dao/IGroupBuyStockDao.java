package com.linrun.infrastructure.dao;

import com.linrun.domain.marketing.model.GroupBuyStock;
import com.linrun.domain.marketing.model.GroupBuyStockFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyStockDao {

    GroupBuyStock queryByActivityId(@Param("activityId") String activityId);

    GroupBuyStock queryByActivityIdAndGoodsIdForUpdate(@Param("activityId") String activityId,
                                                       @Param("goodsId") String goodsId);

    int lockStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int markPaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releaseLockedStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releasePaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    void insertStockFlow(GroupBuyStockFlow flow);
}
