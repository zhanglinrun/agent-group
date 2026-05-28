package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GuideProductPO;
import com.linrun.infrastructure.po.GuideReferencePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideDataDao {

    List<GuideReferencePO> queryReferences(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    List<GuideProductPO> queryCandidateProducts(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    GuideProductPO queryRecommendProduct(@Param("question") String question);

    GuideProductPO queryProductByGoodsId(@Param("goodsId") String goodsId);
}
