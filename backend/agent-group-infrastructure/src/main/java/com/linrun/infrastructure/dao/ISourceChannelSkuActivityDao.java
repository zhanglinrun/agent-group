package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.SourceChannelSkuActivityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ISourceChannelSkuActivityDao {

    SourceChannelSkuActivityPO queryBySourceChannelGoodsId(@Param("source") String source,
                                                           @Param("channel") String channel,
                                                           @Param("goodsId") String goodsId);
}
