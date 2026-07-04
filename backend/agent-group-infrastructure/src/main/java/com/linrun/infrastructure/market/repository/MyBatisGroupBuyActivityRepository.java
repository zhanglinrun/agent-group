package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.infrastructure.market.converter.ActivityPOConverter;
import com.linrun.infrastructure.market.cache.RedisGroupBuyMarketReadCache;
import com.linrun.infrastructure.dao.IGroupBuyActivityDao;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGroupBuyActivityRepository implements GroupBuyActivityRepository {

    private final IGroupBuyActivityDao groupBuyActivityDao;
    private final RedisGroupBuyMarketReadCache readCache;

    public MyBatisGroupBuyActivityRepository(IGroupBuyActivityDao groupBuyActivityDao,
                                             RedisGroupBuyMarketReadCache readCache) {
        this.groupBuyActivityDao = groupBuyActivityDao;
        this.readCache = readCache;
    }

    @Override
    public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
        return readCache.getOrLoad(RedisGroupBuyMarketReadCache.activityByGoodsKey(goodsId), GroupBuyActivity.class,
                () -> Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyActivityDao.queryByGoodsId(goodsId))));
    }

    @Override
    public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
        return readCache.getOrLoad(RedisGroupBuyMarketReadCache.activityByIdKey(activityId), GroupBuyActivity.class,
                () -> Optional.ofNullable(ActivityPOConverter.toEntity(groupBuyActivityDao.queryByActivityId(activityId))));
    }

    @Override
    public List<GroupBuyActivity> queryActivityList(int limit) {
        return ActivityPOConverter.toActivities(groupBuyActivityDao.queryActivityList(Math.max(1, limit)));
    }

    @Override
    public GroupBuyActivity save(GroupBuyActivity activity) {
        groupBuyActivityDao.insertActivity(ActivityPOConverter.toPO(activity));
        evictActivityCache(activity);
        return queryByActivityId(activity.getActivityId()).orElse(activity);
    }

    @Override
    public GroupBuyActivity update(GroupBuyActivity activity) {
        groupBuyActivityDao.updateActivity(ActivityPOConverter.toPO(activity));
        evictActivityCache(activity);
        return queryByActivityId(activity.getActivityId()).orElse(activity);
    }

    @Override
    public boolean updateEnabled(String activityId, boolean enabled) {
        boolean updated = groupBuyActivityDao.updateEnabled(activityId, enabled) > 0;
        if (updated) {
            readCache.evict(RedisGroupBuyMarketReadCache.activityByIdKey(activityId));
        }
        return updated;
    }

    @Override
    public boolean removeByActivityId(String activityId) {
        Optional<GroupBuyActivity> existing = queryByActivityId(activityId);
        boolean removed = groupBuyActivityDao.deleteByActivityId(activityId) > 0;
        if (removed) {
            existing.ifPresent(this::evictActivityCache);
            readCache.evict(RedisGroupBuyMarketReadCache.activityByIdKey(activityId));
        }
        return removed;
    }

    private void evictActivityCache(GroupBuyActivity activity) {
        if (activity == null) {
            return;
        }
        if (StringUtils.hasText(activity.getActivityId())) {
            readCache.evict(RedisGroupBuyMarketReadCache.activityByIdKey(activity.getActivityId()));
        }
        if (StringUtils.hasText(activity.getGoodsId())) {
            readCache.evict(RedisGroupBuyMarketReadCache.activityByGoodsKey(activity.getGoodsId()));
        }
    }
}















