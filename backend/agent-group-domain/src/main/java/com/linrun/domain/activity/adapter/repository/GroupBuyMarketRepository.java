package com.linrun.domain.activity.adapter.repository;

import com.linrun.domain.activity.model.GroupBuyDiscount;
import com.linrun.domain.activity.model.GroupBuyMarketSku;
import com.linrun.domain.activity.model.SourceChannelSkuActivity;

import java.util.List;
import java.util.Optional;

public interface GroupBuyMarketRepository {

    Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId);

    Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId);

    Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId);

    boolean isTagCrowdRange(String tagId, String userId);

    default List<GroupBuyMarketSku> querySkuList(int limit) {
        return List.of();
    }

    default List<SourceChannelSkuActivity> querySourceChannelList(int limit) {
        return List.of();
    }
}
