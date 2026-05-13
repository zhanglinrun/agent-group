package com.linrun.domain.guide.adapter;

import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;

import java.util.List;
import java.util.Optional;

public interface GuideDataRepository {

    List<GuideReference> queryReferences(String question, int limit);

    Optional<GuideProduct> queryRecommendProduct(String question);

    Optional<GuideProduct> queryProductByGoodsId(String goodsId);
}
