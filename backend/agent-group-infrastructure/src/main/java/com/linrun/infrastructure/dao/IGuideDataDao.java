package com.linrun.infrastructure.dao;

import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideDataDao {

    List<GuideReference> queryReferences(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    List<GuideProduct> queryCandidateProducts(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    GuideProduct queryRecommendProduct(@Param("question") String question);

    GuideProduct queryProductByGoodsId(@Param("goodsId") String goodsId);
}
