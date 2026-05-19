package com.linrun.domain.groupbuy.adapter;

import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import com.linrun.domain.groupbuy.model.SourceChannelSkuActivity;

import java.util.Optional;

public interface GroupBuyMarketRepository {

    Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId);

    Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId);

    Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId);

    boolean isTagCrowdRange(String tagId, String userId);
}
