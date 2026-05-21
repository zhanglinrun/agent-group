package com.linrun.trigger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.GroupBuyActivityService;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.RecommendationResult;
import com.linrun.domain.conversation.service.GuideDecisionService;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpToolService {

    private final GuideDecisionService guideDecisionService;
    private final GroupBuyActivityService groupBuyActivityService;
    private final TradeOrderRepository tradeOrderRepository;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public McpToolService(GuideDecisionService guideDecisionService,
                          GroupBuyActivityService groupBuyActivityService,
                          TradeOrderRepository tradeOrderRepository,
                          ToolExecutor toolExecutor,
                          ObjectMapper objectMapper) {
        this.guideDecisionService = guideDecisionService;
        this.groupBuyActivityService = groupBuyActivityService;
        this.tradeOrderRepository = tradeOrderRepository;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                tool("guide_recommend", "根据用户问题返回导购推荐、商品卡片和推荐依据",
                        Map.of("question", stringSchema("用户问题"))),
                tool("group_trial", "根据商品编号返回拼团活动试算结果",
                        Map.of("goodsId", stringSchema("商品编号"))),
                tool("order_status", "根据订单编号返回交易订单和支付单状态",
                        Map.of("orderId", stringSchema("订单编号"))));
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        ToolExecution<Map<String, Object>> execution = toolExecutor.execute(
                "mcp." + name,
                "tools/call",
                "工具调用完成",
                () -> executeTool(name, arguments == null ? Map.of() : arguments));
        if (execution.isSuccess()) {
            return toolResult(false, execution.getResult());
        }
        return toolResult(true, Map.of("message", execution.getMessage()));
    }

    private Map<String, Object> executeTool(String name, Map<String, Object> arguments) {
        return switch (name) {
            case "guide_recommend" -> guideRecommend(text(arguments.get("question")));
            case "group_trial" -> groupTrial(text(arguments.get("goodsId")));
            case "order_status" -> orderStatus(text(arguments.get("orderId")));
            default -> throw new AppException("MCP_0001", "未知工具：" + name);
        };
    }

    private Map<String, Object> guideRecommend(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "用户问题不能为空");
        }
        GuideDecisionResult decisionResult = guideDecisionService.decide(question);
        GuideProduct product = decisionResult.getProduct();
        RecommendationResult recommendation = decisionResult.getRecommendationResult();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intentType", decisionResult.getIntent().getIntentType().name());
        Map<String, Object> productMap = new LinkedHashMap<>();
        productMap.put("goodsId", product.getGoodsId());
        productMap.put("goodsName", product.getGoodsName());
        productMap.put("originPrice", product.getOriginPrice());
        productMap.put("groupPrice", product.getGroupPrice());
        productMap.put("activityId", nullToBlank(product.getActivityId()));
        productMap.put("teamSize", product.getTeamSize());
        productMap.put("remainingSeconds", product.getRemainingSeconds());
        result.put("product", productMap);
        result.put("reasons", recommendation.getReasons().stream()
                .map(reason -> Map.of(
                        "reasonType", reason.getReasonType(),
                        "content", reason.getContent(),
                        "weight", reason.getWeight()))
                .toList());
        result.put("references", decisionResult.getReferences().stream()
                .map(reference -> {
                    Map<String, Object> referenceMap = new LinkedHashMap<>();
                    referenceMap.put("fragmentId", reference.getFragmentId());
                    referenceMap.put("documentId", reference.getDocumentId());
                    referenceMap.put("goodsId", reference.getGoodsId());
                    referenceMap.put("content", reference.getContent());
                    return referenceMap;
                })
                .toList());
        return result;
    }

    private Map<String, Object> groupTrial(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "商品编号不能为空");
        }
        GroupBuyTrialResult trialResult = groupBuyActivityService.trial(goodsId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsId", nullToBlank(trialResult.getGoodsId()));
        result.put("activityId", nullToBlank(trialResult.getActivityId()));
        result.put("available", trialResult.isAvailable());
        result.put("status", trialResult.getStatus().name());
        result.put("groupPrice", trialResult.getGroupPrice());
        result.put("teamSize", trialResult.getTeamSize());
        result.put("remainingSeconds", trialResult.getRemainingSeconds());
        result.put("message", trialResult.getMessage());
        return result;
    }

    private Map<String, Object> orderStatus(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new AppException("0001", "订单编号不能为空");
        }
        TradeOrderEntity tradeOrder = tradeOrderRepository.queryTradeOrderByOrderId(orderId)
                .orElseThrow(() -> new AppException("TRADE_0013", "订单不存在"));
        PayOrderEntity payOrder = tradeOrderRepository.queryPayOrderByOrderId(orderId).orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", tradeOrder.getOrderId());
        result.put("userId", tradeOrder.getUserId());
        result.put("goodsId", tradeOrder.getGoodsId());
        result.put("goodsName", tradeOrder.getGoodsName());
        result.put("buyType", tradeOrder.getBuyType().name());
        result.put("orderStatus", tradeOrder.getOrderStatus().name());
        result.put("payAmount", tradeOrder.getPayAmount());
        if (payOrder != null) {
            result.put("payOrderId", payOrder.getPayOrderId());
            result.put("payChannel", payOrder.getPayChannel());
            result.put("payStatus", payOrder.getPayStatus().name());
            result.put("outTradeNo", nullToBlank(payOrder.getOutTradeNo()));
        }
        return result;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", properties.keySet().stream().toList());
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", schema);
        return tool;
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> toolResult(boolean error, Map<String, Object> result) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", json(result))),
                "isError", error);
    }

    private String json(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
