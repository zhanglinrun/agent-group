package com.linrun.domain.market.adapter.repository;

import com.linrun.domain.market.model.GroupBuyStock;

import java.util.List;
import java.util.Optional;

public interface GroupBuyStockRepository {

    GroupBuyStock lockStock(String activityId, String goodsId, String orderId, String teamId);

    GroupBuyStock markPaidStock(String activityId, String goodsId, String orderId, String teamId);

    GroupBuyStock releaseLockedStock(String activityId, String goodsId, String orderId, String teamId);

    GroupBuyStock releasePaidStock(String activityId, String goodsId, String orderId, String teamId);

    Optional<GroupBuyStock> queryByActivityId(String activityId);

    default List<GroupBuyStock> queryStockList(int limit) {
        return List.of();
    }

    /**
     * 初始化活动库存（新建活动时调用）。available = total。
     */
    default GroupBuyStock initStock(String activityId, String goodsId, int totalStock) {
        return null;
    }

    /**
     * 调整活动总库存（编辑活动时调用）。
     * 新 totalStock 不能小于 lockedStock + paidStock，否则抛 GROUP_0017。
     * availableStock 同步调整为 totalStock - lockedStock - paidStock。
     */
    default GroupBuyStock updateTotalStock(String activityId, int totalStock) {
        return null;
    }

    /**
     * 删除活动库存记录（删除活动时联动调用）。
     */
    default boolean removeByActivityId(String activityId) {
        return false;
    }

    static GroupBuyStockRepository noop() {
        return NoopGroupBuyStockRepository.INSTANCE;
    }

    class NoopGroupBuyStockRepository implements GroupBuyStockRepository {

        private static final NoopGroupBuyStockRepository INSTANCE = new NoopGroupBuyStockRepository();

        @Override
        public GroupBuyStock lockStock(String activityId, String goodsId, String orderId, String teamId) {
            return empty(activityId, goodsId);
        }

        @Override
        public GroupBuyStock markPaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return empty(activityId, goodsId);
        }

        @Override
        public GroupBuyStock releaseLockedStock(String activityId, String goodsId, String orderId, String teamId) {
            return empty(activityId, goodsId);
        }

        @Override
        public GroupBuyStock releasePaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return empty(activityId, goodsId);
        }

        @Override
        public Optional<GroupBuyStock> queryByActivityId(String activityId) {
            return Optional.empty();
        }

        @Override
        public List<GroupBuyStock> queryStockList(int limit) {
            return List.of();
        }

        @Override
        public GroupBuyStock initStock(String activityId, String goodsId, int totalStock) {
            GroupBuyStock stock = empty(activityId, goodsId);
            stock.setTotalStock(totalStock);
            stock.setAvailableStock(totalStock);
            return stock;
        }

        @Override
        public GroupBuyStock updateTotalStock(String activityId, int totalStock) {
            GroupBuyStock stock = empty(activityId, null);
            stock.setTotalStock(totalStock);
            stock.setAvailableStock(totalStock);
            return stock;
        }

        @Override
        public boolean removeByActivityId(String activityId) {
            return true;
        }

        private GroupBuyStock empty(String activityId, String goodsId) {
            GroupBuyStock stock = new GroupBuyStock();
            stock.setActivityId(activityId);
            stock.setGoodsId(goodsId);
            stock.setTotalStock(0);
            stock.setAvailableStock(0);
            stock.setLockedStock(0);
            stock.setPaidStock(0);
            return stock;
        }
    }
}















