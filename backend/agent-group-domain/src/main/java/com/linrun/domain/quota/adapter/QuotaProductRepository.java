package com.linrun.domain.quota.adapter;

import com.linrun.domain.quota.model.QuotaProduct;

import java.util.List;
import java.util.Optional;

public interface QuotaProductRepository {

    List<QuotaProduct> queryCandidateProducts(String question, int limit);

    Optional<QuotaProduct> queryProductByGoodsId(String goodsId);
}















