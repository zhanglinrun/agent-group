package com.linrun.infrastructure.guide.mapper;

import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GuideDataMapper {

    List<GuideReference> queryReferences(@Param("question") String question, @Param("limit") int limit);

    GuideProduct queryRecommendProduct(@Param("question") String question);
}
