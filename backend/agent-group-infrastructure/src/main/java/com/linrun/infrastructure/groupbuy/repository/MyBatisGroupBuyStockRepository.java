package com.linrun.infrastructure.groupbuy.repository;

import com.linrun.domain.groupbuy.adapter.GroupBuyStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyStock;
import com.linrun.domain.groupbuy.model.GroupBuyStockFlow;
import com.linrun.domain.groupbuy.model.GroupBuyStockFlowType;
import com.linrun.infrastructure.dao.IGroupBuyStockDao;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisGroupBuyStockRepository implements GroupBuyStockRepository {

    private static final DateTimeFormatter FLOW_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final IGroupBuyStockDao groupBuyStockDao;

    public MyBatisGroupBuyStockRepository(IGroupBuyStockDao groupBuyStockDao) {
        this.groupBuyStockDao = groupBuyStockDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyStock lockStock(String activityId, String goodsId, String orderId, String teamId) {
        GroupBuyStock before = queryForUpdate(activityId, goodsId);
        int updated = groupBuyStockDao.lockStock(activityId, goodsId);
        if (updated != 1) {
            throw new AppException("GROUP_0012", "拼团库存不足");
        }
        GroupBuyStock after = queryForUpdate(activityId, goodsId);
        insertFlow(activityId, goodsId, orderId, teamId, GroupBuyStockFlowType.LOCK,
                before.getAvailableStock(), after.getAvailableStock(), "stock locked");
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyStock markPaidStock(String activityId, String goodsId, String orderId, String teamId) {
        GroupBuyStock before = queryForUpdate(activityId, goodsId);
        int updated = groupBuyStockDao.markPaidStock(activityId, goodsId);
        if (updated != 1) {
            throw new AppException("GROUP_0013", "拼团库存支付确认失败");
        }
        GroupBuyStock after = queryForUpdate(activityId, goodsId);
        insertFlow(activityId, goodsId, orderId, teamId, GroupBuyStockFlowType.PAY_SUCCESS,
                before.getAvailableStock(), after.getAvailableStock(), "stock paid");
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyStock releaseLockedStock(String activityId, String goodsId, String orderId, String teamId) {
        GroupBuyStock before = queryForUpdate(activityId, goodsId);
        int updated = groupBuyStockDao.releaseLockedStock(activityId, goodsId);
        if (updated != 1) {
            throw new AppException("GROUP_0014", "拼团锁定库存释放失败");
        }
        GroupBuyStock after = queryForUpdate(activityId, goodsId);
        insertFlow(activityId, goodsId, orderId, teamId, GroupBuyStockFlowType.RELEASE_LOCKED,
                before.getAvailableStock(), after.getAvailableStock(), "locked stock released");
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyStock releasePaidStock(String activityId, String goodsId, String orderId, String teamId) {
        GroupBuyStock before = queryForUpdate(activityId, goodsId);
        int updated = groupBuyStockDao.releasePaidStock(activityId, goodsId);
        if (updated != 1) {
            throw new AppException("GROUP_0015", "拼团已支付库存释放失败");
        }
        GroupBuyStock after = queryForUpdate(activityId, goodsId);
        insertFlow(activityId, goodsId, orderId, teamId, GroupBuyStockFlowType.RELEASE_PAID,
                before.getAvailableStock(), after.getAvailableStock(), "paid stock released");
        return after;
    }

    @Override
    public Optional<GroupBuyStock> queryByActivityId(String activityId) {
        return Optional.ofNullable(groupBuyStockDao.queryByActivityId(activityId));
    }

    private GroupBuyStock queryForUpdate(String activityId, String goodsId) {
        GroupBuyStock stock = groupBuyStockDao.queryByActivityIdAndGoodsIdForUpdate(activityId, goodsId);
        if (stock == null) {
            throw new AppException("GROUP_0016", "拼团库存未配置");
        }
        return stock;
    }

    private void insertFlow(String activityId,
                            String goodsId,
                            String orderId,
                            String teamId,
                            GroupBuyStockFlowType flowType,
                            Integer beforeAvailableStock,
                            Integer afterAvailableStock,
                            String remark) {
        GroupBuyStockFlow flow = new GroupBuyStockFlow();
        flow.setFlowId(nextFlowId());
        flow.setActivityId(activityId);
        flow.setGoodsId(goodsId);
        flow.setTeamId(teamId);
        flow.setOrderId(orderId);
        flow.setFlowType(flowType);
        flow.setQuantity(1);
        flow.setBeforeAvailableStock(beforeAvailableStock);
        flow.setAfterAvailableStock(afterAvailableStock);
        flow.setRemark(remark);
        flow.setCreateTime(LocalDateTime.now());
        groupBuyStockDao.insertStockFlow(flow);
    }

    private String nextFlowId() {
        return "SF" + LocalDateTime.now().format(FLOW_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
