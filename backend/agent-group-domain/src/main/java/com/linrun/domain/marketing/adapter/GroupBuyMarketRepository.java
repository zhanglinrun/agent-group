package com.linrun.domain.marketing.adapter;

import com.linrun.domain.marketing.model.GroupBuyDiscount;
import com.linrun.domain.marketing.model.GroupBuyMarketSku;
import com.linrun.domain.marketing.model.SourceChannelSkuActivity;

import java.util.Optional;

public interface GroupBuyMarketRepository {

    Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId);

    Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId);

    Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId);

    boolean isTagCrowdRange(String tagId, String userId);
}
