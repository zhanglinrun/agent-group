package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GuideProductPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideDataDao {

    List<GuideProductPO> queryCandidateProducts(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    GuideProductPO queryProductByGoodsId(@Param("goodsId") String goodsId);
}
