package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyMarketSkuPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyMarketSkuDao {

    GroupBuyMarketSkuPO queryByGoodsId(@Param("goodsId") String goodsId);
}
