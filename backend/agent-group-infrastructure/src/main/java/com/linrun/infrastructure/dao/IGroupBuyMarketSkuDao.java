package com.linrun.infrastructure.dao;

import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyMarketSkuDao {

    GroupBuyMarketSku queryByGoodsId(@Param("goodsId") String goodsId);
}
