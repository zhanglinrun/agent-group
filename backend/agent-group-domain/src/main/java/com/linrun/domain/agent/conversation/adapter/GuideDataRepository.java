package com.linrun.domain.agent.conversation.adapter;

import com.linrun.domain.agent.conversation.model.GuideProduct;

import java.util.List;
import java.util.Optional;

public interface GuideDataRepository {

    List<GuideProduct> queryCandidateProducts(String question, int limit);

    Optional<GuideProduct> queryProductByGoodsId(String goodsId);
}
