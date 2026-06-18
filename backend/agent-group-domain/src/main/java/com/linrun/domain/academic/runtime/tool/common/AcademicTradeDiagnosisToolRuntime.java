package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.api.dto.TradeConsistencyCheckRequest;
import com.linrun.api.dto.TradeConsistencyCheckResponse;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.firstPresent;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.integer;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.text;

/**
 * 异常订单只读诊断工具运行时：把已有的交易一致性巡检能力暴露为 Agent 可调用的工具，
 * 让诊断 Agent 用原生 function-calling 编排"列出订单 → 深度诊断"的只读闭环。
 * 红线：只做只读诊断与结论输出，绝不下单、不补发额度、不退款、不做任何写侧补偿。
 */
public class AcademicTradeDiagnosisToolRuntime {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TradeConsistencyCheckService consistencyCheckService;

    public AcademicTradeDiagnosisToolRuntime(TradeConsistencyCheckService consistencyCheckService) {
        this.consistencyCheckService = consistencyCheckService;
    }

    public static AcademicToolDefinition diagnoseDefinition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.TRADE_DIAGNOSIS)
                .description("Diagnose a single trade order by aggregating order, payment, refund and quota-flow state "
                        + "into a read-only consistency conclusion and handling advice. Read-only: never places orders, "
                        + "grants quota, or issues refunds.")
                .category("trade")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "requestId", Map.of("type", "string", "description", "Request id."),
                                "orderId", Map.of("type", "string", "description", "The trade order id to diagnose.")),
                        "required", List.of("orderId")))
                .requiredArguments(List.of("orderId"))
                .enabled(true)
                .build();
    }

    public static AcademicToolDefinition listDefinition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.TRADE_ORDER_LIST)
                .description("List trade orders for a user (or recent orders when userId is absent) with their consistency "
                        + "conclusion, so the agent can spot abnormal orders before a deep diagnosis. Read-only.")
                .category("trade")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "requestId", Map.of("type", "string", "description", "Request id."),
                                "pageSize", Map.of("type", "integer", "description", "Page size, capped at 100.")),
                        "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput diagnose(AcademicToolCallCommand command) {
        ensureConfigured();
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String currentUserId = requireCurrentUser(command);
        String orderId = text(arguments.get("orderId"));
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("TRADE_DIAG_0001", "orderId is required for trade diagnosis");
        }
        TradeConsistencyCheckResponse response = consistencyCheckService.check(buildRequest(orderId, null, null));
        List<TradeConsistencyCheckResponse.Item> items = response.getItems() == null ? List.of() : response.getItems();
        if (items.isEmpty()) {
            throw new AppException("TRADE_DIAG_0002", "order not found: " + orderId);
        }
        TradeConsistencyCheckResponse.Item item = items.get(0);
        ensureOwner(currentUserId, item);
        Map<String, Object> metadata = fullItemMap(item);
        metadata.put("checkedCount", response.getCheckedCount());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.TRADE_DIAGNOSIS)
                .title("订单诊断：" + orderId)
                .summary(firstPresent(item.getSettlementLabel(), item.getConclusion()))
                .metadata(metadata)
                .build();
    }

    public AcademicToolStructuredOutput list(AcademicToolCallCommand command) {
        ensureConfigured();
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String userId = requireCurrentUser(command);
        int pageSize = clamp(integer(arguments.get("pageSize"), DEFAULT_PAGE_SIZE));
        TradeConsistencyCheckResponse response = consistencyCheckService.check(buildRequest(null, userId, pageSize));
        List<Map<String, Object>> orders = (response.getItems() == null ? List.<TradeConsistencyCheckResponse.Item>of()
                : response.getItems()).stream()
                .map(this::summaryItemMap)
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", userId);
        metadata.put("pageSize", pageSize);
        metadata.put("checkedCount", response.getCheckedCount());
        metadata.put("orders", orders);

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.TRADE_ORDER_LIST)
                .title(StringUtils.hasText(userId) ? "用户订单列表：" + userId : "近期订单列表")
                .summary("orders=" + orders.size())
                .metadata(metadata)
                .build();
    }

    private void ensureConfigured() {
        if (consistencyCheckService == null) {
            throw new AppException("TRADE_DIAG_0003", "trade consistency check service is not configured");
        }
    }

    private String requireCurrentUser(AcademicToolCallCommand command) {
        String currentUserId = command == null ? "" : text(command.getUserId());
        if (!StringUtils.hasText(currentUserId)) {
            throw new AppException("TRADE_DIAG_0004", "current user is required for trade diagnosis");
        }
        return currentUserId;
    }

    private void ensureOwner(String currentUserId, TradeConsistencyCheckResponse.Item item) {
        String ownerUserId = item == null ? "" : text(item.getUserId());
        if (!StringUtils.hasText(ownerUserId) || !ownerUserId.equals(currentUserId)) {
            throw new AppException("TRADE_DIAG_0005", "order does not belong to current user");
        }
    }

    private TradeConsistencyCheckRequest buildRequest(String orderId, String userId, Integer pageSize) {
        TradeConsistencyCheckRequest request = new TradeConsistencyCheckRequest();
        request.setOrderId(orderId);
        request.setUserId(userId);
        request.setPageSize(pageSize);
        return request;
    }

    private Map<String, Object> summaryItemMap(TradeConsistencyCheckResponse.Item item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", item.getOrderId());
        map.put("userId", item.getUserId());
        map.put("buyType", item.getBuyType());
        map.put("orderStatus", item.getOrderStatus());
        map.put("payStatus", item.getPayStatus());
        map.put("settlementLabel", item.getSettlementLabel());
        map.put("conclusion", item.getConclusion());
        map.put("quotaGrantAllowed", item.isQuotaGrantAllowed());
        map.put("refundRollbackRequired", item.isRefundRollbackRequired());
        return map;
    }

    private Map<String, Object> fullItemMap(TradeConsistencyCheckResponse.Item item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", item.getOrderId());
        map.put("userId", item.getUserId());
        map.put("goodsName", item.getGoodsName());
        map.put("goodsId", item.getGoodsId());
        map.put("activityId", item.getActivityId());
        map.put("buyType", item.getBuyType());
        map.put("orderStatus", item.getOrderStatus());
        map.put("originAmount", item.getOriginAmount());
        map.put("orderPayAmount", item.getOrderPayAmount());
        map.put("orderCreateTime", item.getOrderCreateTime());
        map.put("orderPayTime", item.getOrderPayTime());
        map.put("orderCloseTime", item.getOrderCloseTime());
        map.put("payOrderId", item.getPayOrderId());
        map.put("payChannel", item.getPayChannel());
        map.put("payStatus", item.getPayStatus());
        map.put("payAmount", item.getPayAmount());
        map.put("outTradeNo", item.getOutTradeNo());
        map.put("payCreateTime", item.getPayCreateTime());
        map.put("payTime", item.getPayTime());
        map.put("refundId", item.getRefundId());
        map.put("refundStatus", item.getRefundStatus());
        map.put("refundAmount", item.getRefundAmount());
        map.put("refundReason", item.getRefundReason());
        map.put("refundCreateTime", item.getRefundCreateTime());
        map.put("refundTime", item.getRefundTime());
        map.put("quotaGrantFlowExists", item.isQuotaGrantFlowExists());
        map.put("refundRollbackFlowExists", item.isRefundRollbackFlowExists());
        map.put("quotaGrantAllowed", item.isQuotaGrantAllowed());
        map.put("refundRollbackRequired", item.isRefundRollbackRequired());
        map.put("conclusion", item.getConclusion());
        map.put("message", item.getMessage());
        map.put("settlementLabel", item.getSettlementLabel());
        map.put("settlementDetail", item.getSettlementDetail());
        map.put("facts", item.getFacts() == null ? List.of() : item.getFacts());
        return map;
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(value, MAX_PAGE_SIZE));
    }
}
