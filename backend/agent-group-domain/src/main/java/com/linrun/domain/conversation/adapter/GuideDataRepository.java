package com.linrun.domain.conversation.adapter;

import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;

import java.util.List;
import java.util.Optional;

public interface GuideDataRepository {

    List<GuideReference> queryReferences(String question, int limit);

    default List<GuideProduct> queryCandidateProducts(String question, int limit) {
        return queryRecommendProduct(question)
                .map(List::of)
                .orElseGet(List::of);
    }

    Optional<GuideProduct> queryRecommendProduct(String question);

    Optional<GuideProduct> queryProductByGoodsId(String goodsId);
}
