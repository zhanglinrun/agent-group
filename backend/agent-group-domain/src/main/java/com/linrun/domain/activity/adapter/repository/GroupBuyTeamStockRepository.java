package com.linrun.domain.activity.adapter.repository;

import java.time.LocalDateTime;

public interface GroupBuyTeamStockRepository {

    boolean occupyTeamStock(String activityId, String teamId, Integer targetCount, LocalDateTime validEndTime);

    void recoverTeamStock(String activityId, String teamId, String orderId, LocalDateTime validEndTime);

    static GroupBuyTeamStockRepository noop() {
        return NoopGroupBuyTeamStockRepository.INSTANCE;
    }

    class NoopGroupBuyTeamStockRepository implements GroupBuyTeamStockRepository {

        private static final NoopGroupBuyTeamStockRepository INSTANCE = new NoopGroupBuyTeamStockRepository();

        @Override
        public boolean occupyTeamStock(String activityId, String teamId, Integer targetCount, LocalDateTime validEndTime) {
            return true;
        }

        @Override
        public void recoverTeamStock(String activityId, String teamId, String orderId, LocalDateTime validEndTime) {
        }
    }
}
