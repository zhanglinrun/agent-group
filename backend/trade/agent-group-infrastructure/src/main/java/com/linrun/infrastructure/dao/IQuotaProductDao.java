package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.QuotaProductPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IQuotaProductDao {

    List<QuotaProductPO> queryCandidateProducts(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    QuotaProductPO queryProductByGoodsId(@Param("goodsId") String goodsId);
}















