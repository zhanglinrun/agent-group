package com.linrun.infrastructure.dao;

import com.linrun.domain.marketing.model.SourceChannelSkuActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ISourceChannelSkuActivityDao {

    SourceChannelSkuActivity queryBySourceChannelGoodsId(@Param("source") String source,
                                                         @Param("channel") String channel,
                                                         @Param("goodsId") String goodsId);
}
