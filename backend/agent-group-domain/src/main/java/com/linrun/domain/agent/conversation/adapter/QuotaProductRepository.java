package com.linrun.domain.agent.conversation.adapter;

import com.linrun.domain.agent.conversation.model.QuotaProduct;

import java.util.List;
import java.util.Optional;

public interface QuotaProductRepository {

    List<QuotaProduct> queryCandidateProducts(String question, int limit);

    Optional<QuotaProduct> queryProductByGoodsId(String goodsId);
}















