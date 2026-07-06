package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.adapter.repository.GroupBuyTeamStockRepository;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 队伍加入名额的 Redis 预检。
 *
 * 设计约定：
 * - 这里只是数据库行级校验之前的快速预过滤，目的是在高并发下把明显超额的
 *   加入请求挡在数据库之前；最终名额正确性以数据库的队伍计数原子更新为准，
 *   所以 Redis 异常时直接放行（fail-open），不影响主链路可用性。
 * - 占用 = 有界计数：counter 自增后超过可加入名额（目标人数减去建队时队长
 *   已占的 1 个名额）即回退并拒绝。
 * - 释放 = 按订单号幂等：released 集合记录已释放的订单，首次释放才把计数减回，
 *   重复调用（退款重试、补偿任务重放）不会重复加名额。
 */
@Repository
public class RedisGroupBuyTeamStockRepository implements GroupBuyTeamStockRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisGroupBuyTeamStockRepository.class);
    private static final Duration DEFAULT_STOCK_TTL = Duration.ofHours(2);

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    public RedisGroupBuyTeamStockRepository(RedissonClient redissonClient,
                                            @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean occupyTeamStock(String activityId, String teamId, Integer targetCount, LocalDateTime validEndTime) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(teamId) || targetCount == null || targetCount <= 0) {
            return true;
        }
        // 队长在建队时已占 1 个名额，这里管理的是"加入已有队伍"的剩余名额
        long joinCapacity = targetCount - 1L;
        if (joinCapacity <= 0) {
            return false;
        }
        Duration ttl = stockTtl(validEndTime);
        try {
            RAtomicLong occupiedCounter = redissonClient.getAtomicLong(teamStockKey(activityId, teamId));
            long occupied = occupiedCounter.incrementAndGet();
            occupiedCounter.expire(ttl);
            if (occupied > joinCapacity) {
                occupiedCounter.decrementAndGet();
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("group team stock redis fallback, action=occupy, teamId={}, reason={}",
                    teamId, e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void recoverTeamStock(String activityId, String teamId, String orderId, LocalDateTime validEndTime) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(teamId) || !StringUtils.hasText(orderId)) {
            return;
        }
        Duration ttl = stockTtl(validEndTime);
        try {
            RSet<String> releasedOrders = redissonClient.getSet(releasedOrdersKey(activityId, teamId));
            if (!releasedOrders.add(orderId)) {
                return;
            }
            releasedOrders.expire(ttl);
            RAtomicLong occupiedCounter = redissonClient.getAtomicLong(teamStockKey(activityId, teamId));
            long occupied = occupiedCounter.decrementAndGet();
            if (occupied < 0) {
                // 计数被过度释放（如 Redis 键过期后释放先到），归零防御
                occupiedCounter.set(0);
            }
            occupiedCounter.expire(ttl);
        } catch (Exception e) {
            LOGGER.warn("group team stock redis fallback, action=recover, teamId={}, orderId={}, reason={}",
                    teamId, orderId, e.getClass().getSimpleName());
        }
    }

    private Duration stockTtl(LocalDateTime validEndTime) {
        if (validEndTime == null) {
            return DEFAULT_STOCK_TTL;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), validEndTime).plusHours(1);
        return ttl.isNegative() || ttl.isZero() ? DEFAULT_STOCK_TTL : ttl;
    }

    private String teamStockKey(String activityId, String teamId) {
        return keyPrefix + ":group:team-stock:" + activityId + ":" + teamId;
    }

    private String releasedOrdersKey(String activityId, String teamId) {
        return teamStockKey(activityId, teamId) + ":released";
    }
}
