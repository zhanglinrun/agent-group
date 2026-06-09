package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.ApproveHumanApprovalRequest;
import com.linrun.api.dto.CreateHumanApprovalRequest;
import com.linrun.api.dto.HumanApprovalResponse;
import com.linrun.types.exception.AppException;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class HumanApprovalHandler {

    public static final String ACTION_LOCK_MARKET_PAY_ORDER = "LOCK_MARKET_PAY_ORDER";
    public static final String ACTION_SETTLEMENT_MARKET_PAY_ORDER = "SETTLEMENT_MARKET_PAY_ORDER";
    public static final String ACTION_REFUND_MARKET_PAY_ORDER = "REFUND_MARKET_PAY_ORDER";

    private static final Duration APPROVAL_TTL = Duration.ofMinutes(10);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final Map<String, HumanApprovalRecord> fallbackStore = new ConcurrentHashMap<>();
    private final String keyPrefix;
    private final boolean approvalRequired;

    public HumanApprovalHandler() {
        this(null, new ObjectMapper().findAndRegisterModules(), "agent-group", false);
    }

    @Autowired
    public HumanApprovalHandler(RedissonClient redissonClient,
                                ObjectMapper objectMapper,
                                @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix,
                                @Value("${agent.group.hitl.required:false}") boolean approvalRequired) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
        this.approvalRequired = approvalRequired;
    }

    public HumanApprovalResponse createApproval(CreateHumanApprovalRequest request) {
        validateCreateRequest(request);
        LocalDateTime expireTime = LocalDateTime.now().plus(APPROVAL_TTL);
        HumanApprovalRecord record = new HumanApprovalRecord();
        record.setApprovalId("HA" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        record.setUserId(request.getUserId());
        record.setAction(request.getAction());
        record.setBizId(request.getBizId());
        record.setSummary(request.getSummary());
        record.setRiskLevel(StringUtils.hasText(request.getRiskLevel()) ? request.getRiskLevel() : "HIGH");
        record.setAmount(request.getAmount());
        record.setStatus(HumanApprovalRecord.STATUS_WAITING);
        record.setExpireTime(expireTime);
        save(record);
        return toResponse(record, "等待人工确认");
    }

    public HumanApprovalResponse approve(ApproveHumanApprovalRequest request) {
        if (request == null || !StringUtils.hasText(request.getApprovalId())) {
            throw new AppException("HITL_0001", "确认编号不能为空");
        }
        HumanApprovalRecord record = queryRecord(request.getApprovalId());
        if (record.expired(LocalDateTime.now())) {
            throw new AppException("HITL_0002", "人工确认已过�?);
        }
        if (StringUtils.hasText(request.getUserId()) && !request.getUserId().equals(record.getUserId())) {
            throw new AppException("HITL_0003", "人工确认用户不匹�?);
        }
        record.setStatus(Boolean.TRUE.equals(request.getApproved())
                ? HumanApprovalRecord.STATUS_APPROVED
                : HumanApprovalRecord.STATUS_REJECTED);
        record.setReason(request.getReason());
        save(record);
        return toResponse(record, Boolean.TRUE.equals(request.getApproved()) ? "已通过" : "已拒�?);
    }

    public HumanApprovalResponse queryApproval(String approvalId) {
        HumanApprovalRecord record = queryRecord(approvalId);
        return toResponse(record, record.expired(LocalDateTime.now()) ? "已过�? : "正常");
    }

    public void assertApproved(String approvalId, String userId, String action, String bizId) {
        if (!approvalRequired && !StringUtils.hasText(approvalId)) {
            return;
        }
        if (!StringUtils.hasText(approvalId)) {
            throw new AppException("HITL_0004", "该操作需要人工确�?);
        }
        HumanApprovalRecord record = queryRecord(approvalId);
        LocalDateTime now = LocalDateTime.now();
        if (record.expired(now)) {
            throw new AppException("HITL_0002", "人工确认已过�?);
        }
        if (!HumanApprovalRecord.STATUS_APPROVED.equals(record.getStatus())) {
            throw new AppException("HITL_0005", "人工确认未通过");
        }
        if (StringUtils.hasText(userId) && !userId.equals(record.getUserId())) {
            throw new AppException("HITL_0003", "人工确认用户不匹�?);
        }
        if (StringUtils.hasText(action) && !action.equals(record.getAction())) {
            throw new AppException("HITL_0006", "人工确认操作不匹�?);
        }
        if (StringUtils.hasText(bizId) && StringUtils.hasText(record.getBizId()) && !bizId.equals(record.getBizId())) {
            throw new AppException("HITL_0007", "人工确认业务编号不匹�?);
        }
        record.setStatus(HumanApprovalRecord.STATUS_CONSUMED);
        save(record);
    }

    private void validateCreateRequest(CreateHumanApprovalRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUserId())
                || !StringUtils.hasText(request.getAction())
                || !StringUtils.hasText(request.getBizId())) {
            throw new AppException("HITL_0001", "用户、操作和业务编号不能为空");
        }
    }

    private HumanApprovalRecord queryRecord(String approvalId) {
        if (!StringUtils.hasText(approvalId)) {
            throw new AppException("HITL_0001", "确认编号不能为空");
        }
        HumanApprovalRecord record = read(approvalId);
        if (record == null) {
            throw new AppException("HITL_0008", "人工确认记录不存�?);
        }
        return record;
    }

    private void save(HumanApprovalRecord record) {
        fallbackStore.put(record.getApprovalId(), record);
        if (redissonClient == null) {
            return;
        }
        try {
            cache().put(record.getApprovalId(), objectMapper.writeValueAsString(record),
                    APPROVAL_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
            fallbackStore.put(record.getApprovalId(), record);
        }
    }

    private HumanApprovalRecord read(String approvalId) {
        if (redissonClient != null) {
            try {
                String value = cache().get(approvalId);
                if (StringUtils.hasText(value)) {
                    return objectMapper.readValue(value, HumanApprovalRecord.class);
                }
            } catch (Exception ignored) {
                return fallbackStore.get(approvalId);
            }
        }
        return fallbackStore.get(approvalId);
    }

    private RMapCache<String, String> cache() {
        return redissonClient.getMapCache(keyPrefix + ":hitl:approvals");
    }

    private HumanApprovalResponse toResponse(HumanApprovalRecord record, String message) {
        HumanApprovalResponse response = new HumanApprovalResponse();
        response.setApprovalId(record.getApprovalId());
        response.setUserId(record.getUserId());
        response.setAction(record.getAction());
        response.setBizId(record.getBizId());
        response.setSummary(record.getSummary());
        response.setRiskLevel(record.getRiskLevel());
        response.setAmount(record.getAmount());
        response.setStatus(record.getStatus());
        response.setReason(record.getReason());
        response.setExpireTime(record.getExpireTime());
        response.setMessage(message);
        return response;
    }
}















