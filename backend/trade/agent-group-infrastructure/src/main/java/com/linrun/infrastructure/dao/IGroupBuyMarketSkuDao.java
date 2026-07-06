package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyMarketSkuPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGroupBuyMarketSkuDao {

    GroupBuyMarketSkuPO queryByGoodsId(@Param("goodsId") String goodsId);

    List<GroupBuyMarketSkuPO> querySkuList(@Param("limit") int limit);
}















