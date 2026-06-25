package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyStockFlowPO;
import com.linrun.infrastructure.po.GroupBuyStockPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGroupBuyStockDao {

    GroupBuyStockPO queryByActivityId(@Param("activityId") String activityId);

    GroupBuyStockPO queryByActivityIdAndGoodsIdForUpdate(@Param("activityId") String activityId,
                                                         @Param("goodsId") String goodsId);

    List<GroupBuyStockPO> queryStockList(@Param("limit") int limit);

    int lockStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int markPaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releaseLockedStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    int releasePaidStock(@Param("activityId") String activityId, @Param("goodsId") String goodsId);

    void insertStockFlow(GroupBuyStockFlowPO flow);

    int insertStock(GroupBuyStockPO stock);

    /**
     * 调整总库存：available 同步为 total - locked - paid。
     * where 条件保证 total >= locked + paid，否则不更新（返回 0）。
     */
    int updateTotalStock(@Param("activityId") String activityId, @Param("totalStock") int totalStock);

    int deleteByActivityId(@Param("activityId") String activityId);
}















