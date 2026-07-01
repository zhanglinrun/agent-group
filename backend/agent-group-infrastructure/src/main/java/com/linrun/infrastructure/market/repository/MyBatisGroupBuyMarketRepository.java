package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.model.GroupBuyMarketSku;
import com.linrun.domain.market.model.SourceChannelSkuActivity;
import com.linrun.infrastructure.market.converter.ActivityPOConverter;
import com.linrun.infrastructure.dao.ICrowdTagDao;
import com.linrun.infrastructure.dao.IGroupBuyDiscountDao;
import com.linrun.infrastructure.dao.IGroupBuyMarketSkuDao;
import com.linrun.infrastructure.dao.ISourceChannelSkuActivityDao;
import com.linrun.infrastructure.po.GroupBuyDiscountPO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
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
        return Optional.ofNullable(ActivityPOConverter.toEntity(skuDao.queryByGoodsId(goodsId)));
    }

    @Override
    public Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(
                sourceChannelSkuActivityDao.queryBySourceChannelGoodsId(source, channel, goodsId)));
    }

    @Override
    public Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId) {
        return Optional.ofNullable(ActivityPOConverter.toEntity(discountDao.queryByDiscountId(discountId)));
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

    @Override
    public List<GroupBuyMarketSku> querySkuList(int limit) {
        return ActivityPOConverter.toMarketSkus(skuDao.querySkuList(Math.max(1, limit)));
    }

    @Override
    public List<SourceChannelSkuActivity> querySourceChannelList(int limit) {
        return ActivityPOConverter.toSourceChannels(sourceChannelSkuActivityDao.querySourceChannelList(Math.max(1, limit)));
    }

    @Override
    public List<GroupBuyDiscount> queryDiscountList(int limit) {
        return ActivityPOConverter.toDiscounts(discountDao.queryDiscountList(Math.max(1, limit)));
    }

    @Override
    public GroupBuyDiscount saveDiscount(GroupBuyDiscount discount) {
        if (discount == null || !StringUtils.hasText(discount.getDiscountId())) {
            return null;
        }
        GroupBuyDiscountPO existing = discountDao.queryByDiscountId(discount.getDiscountId());
        GroupBuyDiscountPO po = ActivityPOConverter.toPO(discount);
        if (po.getEnabled() == null) {
            po.setEnabled(Boolean.TRUE);
        }
        if (existing == null) {
            discountDao.insertDiscount(po);
        } else {
            discountDao.updateDiscount(po);
        }
        return ActivityPOConverter.toEntity(discountDao.queryByDiscountId(discount.getDiscountId()));
    }

    @Override
    public boolean updateDiscountEnabled(String discountId, boolean enabled) {
        if (!StringUtils.hasText(discountId)) {
            return false;
        }
        return discountDao.updateDiscountEnabled(discountId, enabled) > 0;
    }

    @Override
    public boolean deleteDiscount(String discountId) {
        if (!StringUtils.hasText(discountId)) {
            return false;
        }
        return discountDao.deleteByDiscountId(discountId) > 0;
    }
}















