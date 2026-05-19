package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import com.linrun.domain.groupbuy.model.SourceChannelSkuActivity;
import com.linrun.infrastructure.dao.ICrowdTagDao;
import com.linrun.infrastructure.dao.IGroupBuyDiscountDao;
import com.linrun.infrastructure.dao.IGroupBuyMarketSkuDao;
import com.linrun.infrastructure.dao.ISourceChannelSkuActivityDao;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
public class MyBatisGroupBuyMarketRepository implements GroupBuyMarketRepository {

    private final IGroupBuyMarketSkuDao skuDao;
    private final ISourceChannelSkuActivityDao sourceChannelSkuActivityDao;
    private final IGroupBuyDiscountDao discountDao;
    private final ICrowdTagDao crowdTagDao;

    public MyBatisGroupBuyMarketRepository(IGroupBuyMarketSkuDao skuDao,
                                           ISourceChannelSkuActivityDao sourceChannelSkuActivityDao,
                                           IGroupBuyDiscountDao discountDao,
                                           ICrowdTagDao crowdTagDao) {
        this.skuDao = skuDao;
        this.sourceChannelSkuActivityDao = sourceChannelSkuActivityDao;
        this.discountDao = discountDao;
        this.crowdTagDao = crowdTagDao;
    }

    @Override
    public Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId) {
        return Optional.ofNullable(skuDao.queryByGoodsId(goodsId));
    }

    @Override
    public Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId) {
        return Optional.ofNullable(sourceChannelSkuActivityDao.queryBySourceChannelGoodsId(source, channel, goodsId));
    }

    @Override
    public Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId) {
        return Optional.ofNullable(discountDao.queryByDiscountId(discountId));
    }

    @Override
    public boolean isTagCrowdRange(String tagId, String userId) {
        if (!StringUtils.hasText(tagId)) {
            return true;
        }
        int crowdCount = crowdTagDao.countCrowdTagUsers(tagId);
        if (crowdCount == 0) {
            return true;
        }
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        return crowdTagDao.isTagCrowdRange(tagId, userId);
    }
}
