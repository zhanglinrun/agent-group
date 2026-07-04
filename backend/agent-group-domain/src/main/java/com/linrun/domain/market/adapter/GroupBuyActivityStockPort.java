package com.linrun.domain.market.adapter;

/**
 * 活动级可用库存 Redis 预扣（fail-open）。
 * 在 DB {@code SELECT FOR UPDATE} 之前快速过滤明显无库存请求，降低行锁竞争。
 */
public interface GroupBuyActivityStockPort {

    /**
     * 预扣 1 个可用库存名额；Redis 不可用时返回 true（放行，由 DB 兜底）。
     */
    boolean tryReserve(String activityId, String orderId, int dbAvailableStock);

    /**
     * 锁单失败时按订单幂等回滚预扣。
     */
    void release(String activityId, String orderId);

    static GroupBuyActivityStockPort noop() {
        return new GroupBuyActivityStockPort() {
            @Override
            public boolean tryReserve(String activityId, String orderId, int dbAvailableStock) {
                return true;
            }

            @Override
            public void release(String activityId, String orderId) {
            }
        };
    }
}
