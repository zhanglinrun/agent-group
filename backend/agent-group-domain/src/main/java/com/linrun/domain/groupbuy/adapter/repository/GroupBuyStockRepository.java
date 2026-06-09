package com.linrun.domain.groupbuy.adapter.repository;

import com.linrun.domain.groupbuy.model.GroupBuyStock;

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















