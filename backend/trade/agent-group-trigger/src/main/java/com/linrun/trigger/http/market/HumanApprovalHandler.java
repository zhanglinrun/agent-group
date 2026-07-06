package com.linrun.trigger.http.market;

import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HumanApprovalHandler {

    public static final String ACTION_LOCK_MARKET_PAY_ORDER = "LOCK_MARKET_PAY_ORDER";
    public static final String ACTION_SETTLEMENT_MARKET_PAY_ORDER = "SETTLEMENT_MARKET_PAY_ORDER";
    public static final String ACTION_REFUND_MARKET_PAY_ORDER = "REFUND_MARKET_PAY_ORDER";

    private final Map<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();

    public Map<String, Object> createApproval(Map<String, Object> request) {
        String approvalId = "approval-" + System.currentTimeMillis();
        ApprovalRecord record = new ApprovalRecord(
                approvalId,
                text(request, "userId"),
                text(request, "action"),
                text(request, "bizId"),
                false,
                LocalDateTime.now().plusMinutes(10)
        );
        approvals.put(approvalId, record);
        return Map.of(
                "approvalId", approvalId,
                "approved", false,
                "expireTime", record.expireTime()
        );
    }

    public Map<String, Object> approve(String approvalId, Map<String, Object> request) {
        ApprovalRecord record = requireRecord(approvalId);
        ApprovalRecord approved = new ApprovalRecord(
                record.approvalId(),
                StringUtils.hasText(text(request, "userId")) ? text(request, "userId") : record.userId(),
                StringUtils.hasText(text(request, "action")) ? text(request, "action") : record.action(),
                StringUtils.hasText(text(request, "bizId")) ? text(request, "bizId") : record.bizId(),
                true,
                record.expireTime()
        );
        approvals.put(approvalId, approved);
        return Map.of(
                "approvalId", approvalId,
                "approved", true,
                "expireTime", approved.expireTime()
        );
    }

    public void assertApproved(String approvalId, String userId, String action, String bizId) {
        if (!StringUtils.hasText(approvalId)) {
            throw new AppException("HITL_0001", "human approval required");
        }
        ApprovalRecord record = requireRecord(approvalId);
        if (record.expireTime().isBefore(LocalDateTime.now())) {
            throw new AppException("HITL_0002", "human approval expired");
        }
        if (StringUtils.hasText(record.userId()) && !record.userId().equals(userId)) {
            throw new AppException("HITL_0003", "human approval user mismatch");
        }
        if (!record.approved()) {
            throw new AppException("HITL_0004", "human approval is not approved");
        }
        if (StringUtils.hasText(record.action()) && !record.action().equals(action)) {
            throw new AppException("HITL_0005", "human approval action mismatch");
        }
        if (StringUtils.hasText(record.bizId()) && !record.bizId().equals(bizId)) {
            throw new AppException("HITL_0006", "human approval biz mismatch");
        }
    }

    private ApprovalRecord requireRecord(String approvalId) {
        ApprovalRecord record = approvals.get(approvalId);
        if (record == null) {
            throw new AppException("HITL_0007", "human approval not found");
        }
        return record;
    }

    private String text(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ApprovalRecord(String approvalId,
                                  String userId,
                                  String action,
                                  String bizId,
                                  boolean approved,
                                  LocalDateTime expireTime) {
    }
}
