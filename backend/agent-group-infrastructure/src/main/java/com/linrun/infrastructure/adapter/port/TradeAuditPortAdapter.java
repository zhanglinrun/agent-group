package com.linrun.infrastructure.adapter.port;

import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.model.GroupBuyOrderLock;
import com.linrun.domain.activity.model.GroupBuyTeam;
import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.adapter.repository.TradeStatusFlowRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TradeAuditPortAdapter implements AcademicTradeAuditPort {

    private final TradeOrderRepository tradeOrderRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final TradeStatusFlowRepository tradeStatusFlowRepository;

    public TradeAuditPortAdapter(TradeOrderRepository tradeOrderRepository,
                                 GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                 UserQuotaRepository userQuotaRepository,
                                 TradeStatusFlowRepository tradeStatusFlowRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.userQuotaRepository = userQuotaRepository;
        this.tradeStatusFlowRepository = tradeStatusFlowRepository;
    }

    @Override
    public AcademicTradeAuditResult audit(AcademicTradeAuditRequest request) {
        String userId = request == null ? "" : text(request.userId());
        if (!StringUtils.hasText(userId)) {
            return failure("missing user id");
        }

        String orderId = request == null ? "" : text(request.orderId());
        String teamId = request == null ? "" : text(request.teamId());
        int orderLimit = request == null ? 8 : Math.max(1, Math.min(request.recentOrderLimit(), 50));
        int flowLimit = request == null ? 20 : Math.max(1, Math.min(request.recentFlowLimit(), 100));
        boolean includeRecentFlows = request == null || request.includeRecentFlows();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<Map<String, Object>> findings = new ArrayList<>();
        snapshot.put("userId", userId);
        snapshot.put("orderId", orderId);
        snapshot.put("teamId", teamId);
        snapshot.put("generatedAt", LocalDateTime.now().toString());
        snapshot.put("quotaAccount", userQuotaRepository.queryAccount(userId).map(this::account).orElse(Map.of()));

        if (StringUtils.hasText(orderId)) {
            auditOrder(userId, orderId, teamId, snapshot, findings);
        } else {
            List<TradeOrderEntity> orders = tradeOrderRepository.queryUserTradeOrders(
                    userId,
                    null,
                    orderLimit,
                    null,
                    "",
                    request == null ? "" : text(request.keyword()));
            snapshot.put("recentOrders", orders.stream().map(this::tradeOrder).toList());
            if (orders.isEmpty()) {
                findings.add(finding("INFO", "NO_RECENT_ORDER", "No recent trade order matched current query."));
            }
        }

        if (includeRecentFlows) {
            snapshot.put("recentQuotaFlows", userQuotaRepository.queryRecentFlows(userId, flowLimit).stream()
                    .map(this::quotaFlow)
                    .toList());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("orderScoped", StringUtils.hasText(orderId));
        metadata.put("recentOrderLimit", orderLimit);
        metadata.put("recentFlowLimit", flowLimit);
        metadata.put("includeRecentFlows", includeRecentFlows);
        metadata.put("highestSeverity", highestSeverity(findings));

        return new AcademicTradeAuditResult(true, summary(findings), snapshot, findings, metadata, "");
    }

    private void auditOrder(String userId,
                            String orderId,
                            String requestedTeamId,
                            Map<String, Object> snapshot,
                            List<Map<String, Object>> findings) {
        TradeOrderEntity order = tradeOrderRepository.queryTradeOrderByOrderId(orderId).orElse(null);
        if (order == null) {
            findings.add(finding("WARN", "ORDER_NOT_FOUND", "Order was not found in backend trade system."));
            return;
        }
        if (!userId.equals(order.getUserId())) {
            findings.add(finding("ERROR", "ORDER_OWNER_MISMATCH", "Order does not belong to current user."));
            snapshot.put("tradeOrder", Map.of("orderId", orderId, "accessible", false));
            return;
        }

        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId).orElse(null);
        RefundOrderEntity refundOrder = tradeOrderRepository.queryRefundOrderByOrderId(orderId).orElse(null);
        GroupBuyOrderLock orderLock = groupBuyOrderLockRepository.queryLockByOrderId(orderId).orElse(null);
        String teamId = firstPresent(requestedTeamId, orderLock == null ? "" : orderLock.getTeamId());
        GroupBuyTeam team = StringUtils.hasText(teamId)
                ? groupBuyOrderLockRepository.queryTeamByTeamId(teamId).orElse(null)
                : null;
        UserQuotaFlow grantFlow = userQuotaRepository.queryFlow(
                userId, UserQuotaService.FLOW_ORDER_GRANT, orderId).orElse(null);
        UserQuotaFlow rollbackFlow = userQuotaRepository.queryFlow(
                userId, UserQuotaService.FLOW_REFUND_ROLLBACK, orderId).orElse(null);
        List<TradeStatusFlowEntity> statusFlows = tradeStatusFlowRepository.queryByOrderId(orderId);

        snapshot.put("tradeOrder", tradeOrder(order));
        snapshot.put("payOrder", payOrder == null ? Map.of() : payOrder(payOrder));
        snapshot.put("refundOrder", refundOrder == null ? Map.of() : refundOrder(refundOrder));
        snapshot.put("groupLock", orderLock == null ? Map.of() : groupLock(orderLock));
        snapshot.put("groupTeam", team == null ? Map.of() : groupTeam(team));
        snapshot.put("statusFlows", statusFlows.stream().map(this::statusFlow).toList());
        snapshot.put("orderGrantFlow", grantFlow == null ? Map.of() : quotaFlow(grantFlow));
        snapshot.put("refundRollbackFlow", rollbackFlow == null ? Map.of() : quotaFlow(rollbackFlow));
        snapshot.put("auditFlags", auditFlags(order, payOrder, refundOrder, team, grantFlow, rollbackFlow));

        collectFindings(order, payOrder, refundOrder, orderLock, team, grantFlow, rollbackFlow, findings);
    }

    private void collectFindings(TradeOrderEntity order,
                                 PayOrderEntity payOrder,
                                 RefundOrderEntity refundOrder,
                                 GroupBuyOrderLock orderLock,
                                 GroupBuyTeam team,
                                 UserQuotaFlow grantFlow,
                                 UserQuotaFlow rollbackFlow,
                                 List<Map<String, Object>> findings) {
        boolean groupBuy = TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType());
        boolean grantable = isGrantable(order);
        boolean granted = grantFlow != null;
        boolean refunded = isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.REFUNDED) || refundOrder != null;
        boolean paid = payOrder != null && "SUCCESS".equals(statusName(payOrder.getPayStatus()));

        if (payOrder == null && isPaidLike(order)) {
            findings.add(finding("WARN", "PAY_ORDER_MISSING", "Order status is paid-like but pay order is missing."));
        }
        if (payOrder != null && order.getPayAmount() != null && payOrder.getPayAmount() != null
                && order.getPayAmount().compareTo(payOrder.getPayAmount()) != 0) {
            findings.add(finding("WARN", "PAY_AMOUNT_MISMATCH", "Trade order amount and pay order amount are inconsistent."));
        }
        if (groupBuy && orderLock == null) {
            findings.add(finding("WARN", "GROUP_LOCK_MISSING", "Group-buy order has no group lock record."));
        }
        if (groupBuy && orderLock != null && team == null) {
            findings.add(finding("WARN", "GROUP_TEAM_MISSING", "Group-buy lock exists but team record is missing."));
        }
        if (groupBuy && paid && isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.PAY_SUCCESS) && !granted) {
            findings.add(finding("INFO", "PAID_WAITING_GROUP_SETTLEMENT", "Payment succeeded, but group settlement has not completed; quota should not be granted yet."));
        }
        if (groupBuy && paid && isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.PAY_SUCCESS) && granted) {
            findings.add(finding("ERROR", "QUOTA_GRANTED_BEFORE_GROUP_SETTLEMENT", "Group-buy quota was granted before group settlement."));
        }
        if (grantable && !granted) {
            findings.add(finding("WARN", "QUOTA_GRANT_MISSING", "Order is grantable, but quota grant flow was not found."));
        }
        if (!grantable && granted && !refunded) {
            findings.add(finding("ERROR", "QUOTA_GRANTED_FOR_UNGRANTABLE_ORDER", "Quota grant flow exists while order is not grantable."));
        }
        if (refunded && granted && rollbackFlow == null) {
            findings.add(finding("ERROR", "REFUND_ROLLBACK_MISSING", "Refunded order has quota grant flow but no rollback flow."));
        }
        if (refunded && rollbackFlow != null && grantFlow == null) {
            findings.add(finding("WARN", "ROLLBACK_WITHOUT_GRANT", "Rollback flow exists but grant flow was not found."));
        }
        if (findings.isEmpty()) {
            findings.add(finding("INFO", "NO_BLOCKING_RISK", "No blocking risk was found from backend trade facts."));
        }
    }

    private Map<String, Object> auditFlags(TradeOrderEntity order,
                                           PayOrderEntity payOrder,
                                           RefundOrderEntity refundOrder,
                                           GroupBuyTeam team,
                                           UserQuotaFlow grantFlow,
                                           UserQuotaFlow rollbackFlow) {
        Map<String, Object> flags = new LinkedHashMap<>();
        boolean groupBuy = TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType());
        flags.put("groupBuy", groupBuy);
        flags.put("paid", payOrder != null && "SUCCESS".equals(statusName(payOrder.getPayStatus())));
        flags.put("groupSettled", !groupBuy
                || isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.GROUP_SETTLED)
                || isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.DEAL_DONE)
                || (team != null && "SUCCESS".equals(statusName(team.getTeamStatus()))));
        flags.put("quotaGrantable", isGrantable(order));
        flags.put("quotaGranted", grantFlow != null);
        flags.put("refunded", refundOrder != null || isStatus(order.getOrderStatus(), TradeOrderStatusEnumVO.REFUNDED));
        flags.put("refundRolledBack", rollbackFlow != null);
        return flags;
    }

    private boolean isGrantable(TradeOrderEntity order) {
        if (order == null) {
            return false;
        }
        TradeOrderStatusEnumVO status = order.getOrderStatus();
        if (TradeBuyTypeEnumVO.GROUP_BUY.equals(order.getBuyType())) {
            return isStatus(status, TradeOrderStatusEnumVO.GROUP_SETTLED)
                    || isStatus(status, TradeOrderStatusEnumVO.DEAL_DONE);
        }
        return isStatus(status, TradeOrderStatusEnumVO.PAY_SUCCESS)
                || isStatus(status, TradeOrderStatusEnumVO.DEAL_DONE);
    }

    private boolean isPaidLike(TradeOrderEntity order) {
        if (order == null) {
            return false;
        }
        TradeOrderStatusEnumVO status = order.getOrderStatus();
        return isStatus(status, TradeOrderStatusEnumVO.PAY_SUCCESS)
                || isStatus(status, TradeOrderStatusEnumVO.GROUP_SETTLED)
                || isStatus(status, TradeOrderStatusEnumVO.DEAL_DONE)
                || isStatus(status, TradeOrderStatusEnumVO.REFUNDED);
    }

    private boolean isStatus(TradeOrderStatusEnumVO actual, TradeOrderStatusEnumVO expected) {
        return expected != null && expected.equals(actual);
    }

    private Map<String, Object> tradeOrder(TradeOrderEntity order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("orderId", order.getOrderId());
        map.put("userId", order.getUserId());
        map.put("goodsId", order.getGoodsId());
        map.put("goodsName", order.getGoodsName());
        map.put("activityId", order.getActivityId());
        map.put("buyType", statusName(order.getBuyType()));
        map.put("originAmount", amount(order.getOriginAmount()));
        map.put("payAmount", amount(order.getPayAmount()));
        map.put("orderStatus", statusName(order.getOrderStatus()));
        map.put("createTime", time(order.getCreateTime()));
        map.put("payTime", time(order.getPayTime()));
        map.put("closeTime", time(order.getCloseTime()));
        return map;
    }

    private Map<String, Object> payOrder(PayOrderEntity payOrder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("payOrderId", payOrder.getPayOrderId());
        map.put("orderId", payOrder.getOrderId());
        map.put("payChannel", payOrder.getPayChannel());
        map.put("payAmount", amount(payOrder.getPayAmount()));
        map.put("payStatus", statusName(payOrder.getPayStatus()));
        map.put("outTradeNo", payOrder.getOutTradeNo());
        map.put("createTime", time(payOrder.getCreateTime()));
        map.put("payTime", time(payOrder.getPayTime()));
        return map;
    }

    private Map<String, Object> refundOrder(RefundOrderEntity refundOrder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("refundId", refundOrder.getRefundId());
        map.put("orderId", refundOrder.getOrderId());
        map.put("payOrderId", refundOrder.getPayOrderId());
        map.put("userId", refundOrder.getUserId());
        map.put("refundAmount", amount(refundOrder.getRefundAmount()));
        map.put("refundStatus", statusName(refundOrder.getRefundStatus()));
        map.put("refundReason", refundOrder.getRefundReason());
        map.put("createTime", time(refundOrder.getCreateTime()));
        map.put("refundTime", time(refundOrder.getRefundTime()));
        return map;
    }

    private Map<String, Object> groupLock(GroupBuyOrderLock lock) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lockId", lock.getLockId());
        map.put("userId", lock.getUserId());
        map.put("teamId", lock.getTeamId());
        map.put("orderId", lock.getOrderId());
        map.put("activityId", lock.getActivityId());
        map.put("goodsId", lock.getGoodsId());
        map.put("lockAmount", amount(lock.getLockAmount()));
        map.put("lockStatus", statusName(lock.getLockStatus()));
        map.put("lockTime", time(lock.getLockTime()));
        return map;
    }

    private Map<String, Object> groupTeam(GroupBuyTeam team) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("teamId", team.getTeamId());
        map.put("activityId", team.getActivityId());
        map.put("goodsId", team.getGoodsId());
        map.put("targetCount", team.getTargetCount());
        map.put("completeCount", team.getCompleteCount());
        map.put("lockCount", team.getLockCount());
        map.put("remainingCount", team.remainingCount());
        map.put("teamStatus", statusName(team.getTeamStatus()));
        map.put("validStartTime", time(team.getValidStartTime()));
        map.put("validEndTime", time(team.getValidEndTime()));
        map.put("createTime", time(team.getCreateTime()));
        return map;
    }

    private Map<String, Object> quotaFlow(UserQuotaFlow flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("flowId", flow.getFlowId());
        map.put("userId", flow.getUserId());
        map.put("flowType", flow.getFlowType());
        map.put("bizId", flow.getBizId());
        map.put("quotaAmount", amount(flow.getQuotaAmount()));
        map.put("beforeBalance", amount(flow.getBeforeBalance()));
        map.put("afterBalance", amount(flow.getAfterBalance()));
        map.put("remark", flow.getRemark());
        map.put("createTime", time(flow.getCreateTime()));
        return map;
    }

    private Map<String, Object> account(UserQuotaAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", account.getUserId());
        map.put("quotaBalance", amount(account.getQuotaBalance()));
        map.put("frozenQuota", amount(account.getFrozenQuota()));
        map.put("usedQuota", amount(account.getUsedQuota()));
        return map;
    }

    private Map<String, Object> statusFlow(TradeStatusFlowEntity flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("flowId", flow.getFlowId());
        map.put("orderId", flow.getOrderId());
        map.put("bizType", flow.getBizType());
        map.put("bizId", flow.getBizId());
        map.put("eventType", flow.getEventType());
        map.put("fromStatus", flow.getFromStatus());
        map.put("toStatus", flow.getToStatus());
        map.put("remark", flow.getRemark());
        map.put("createTime", time(flow.getCreateTime()));
        return map;
    }

    private Map<String, Object> finding(String severity, String code, String message) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("severity", severity);
        finding.put("code", code);
        finding.put("message", message);
        return finding;
    }

    private String summary(List<Map<String, Object>> findings) {
        String highest = highestSeverity(findings);
        long risks = findings == null ? 0 : findings.stream()
                .filter(item -> !"INFO".equals(item.get("severity")))
                .count();
        if (risks == 0) {
            return "trade facts checked, no blocking risk";
        }
        return "trade facts checked, highestSeverity=" + highest + ", riskCount=" + risks;
    }

    private String highestSeverity(List<Map<String, Object>> findings) {
        if (findings == null || findings.isEmpty()) {
            return "INFO";
        }
        if (hasSeverity(findings, "ERROR")) {
            return "ERROR";
        }
        if (hasSeverity(findings, "WARN")) {
            return "WARN";
        }
        return "INFO";
    }

    private boolean hasSeverity(List<Map<String, Object>> findings, String severity) {
        return findings.stream().anyMatch(item -> severity.equals(item.get("severity")));
    }

    private AcademicTradeAuditResult failure(String message) {
        return new AcademicTradeAuditResult(false, "", Map.of(), List.of(), Map.of(), message);
    }

    private String statusName(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String amount(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
