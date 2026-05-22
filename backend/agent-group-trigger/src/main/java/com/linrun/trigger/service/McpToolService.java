package com.linrun.trigger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.agent.response.OrderDeltaDTO;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.GroupBuyActivityService;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.RecommendationResult;
import com.linrun.domain.conversation.service.AgentToolRegistry;
import com.linrun.domain.conversation.service.GuideDecisionService;
import com.linrun.domain.conversation.service.KnowledgeSearchToolService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpToolService {

    private final GuideDecisionService guideDecisionService;
    private final KnowledgeSearchToolService knowledgeSearchToolService;
    private final GroupBuyActivityService groupBuyActivityService;
    private final OrderStatusToolService orderStatusToolService;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public McpToolService(GuideDecisionService guideDecisionService,
                          KnowledgeSearchToolService knowledgeSearchToolService,
                          GroupBuyActivityService groupBuyActivityService,
                          OrderStatusToolService orderStatusToolService,
                          ToolExecutor toolExecutor,
                          ObjectMapper objectMapper) {
        this.guideDecisionService = guideDecisionService;
        this.knowledgeSearchToolService = knowledgeSearchToolService;
        this.groupBuyActivityService = groupBuyActivityService;
        this.orderStatusToolService = orderStatusToolService;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                tool(AgentToolRegistry.KNOWLEDGE_SEARCH, "根据用户问题检索知识库片段",
                        Map.of("question", stringSchema("用户问题"))),
                tool(AgentToolRegistry.GUIDE_RECOMMEND, "根据用户问题返回导购推荐、商品卡片和推荐依据",
                        Map.of("question", stringSchema("用户问题"))),
                tool(AgentToolRegistry.GROUP_TRIAL, "根据商品编号返回拼团活动试算结果",
                        Map.of("goodsId", stringSchema("商品编号"))),
                tool(AgentToolRegistry.ORDER_STATUS, "根据订单编号返回交易订单和支付单状态",
                        Map.of(
                                "orderId", stringSchema("订单编号"),
                                "question", stringSchema("用户原始订单问题"),
                                "userId", stringSchema("用户编号")),
                        List.of()));
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
            case AgentToolRegistry.KNOWLEDGE_SEARCH -> knowledgeSearch(text(arguments.get("question")));
            case AgentToolRegistry.GUIDE_RECOMMEND -> guideRecommend(text(arguments.get("question")));
            case AgentToolRegistry.GROUP_TRIAL -> groupTrial(text(arguments.get("goodsId")));
            case AgentToolRegistry.ORDER_STATUS -> orderStatus(
                    text(arguments.get("orderId")),
                    text(arguments.get("question")),
                    text(arguments.get("userId")));
            default -> throw new AppException("MCP_0001", "未知工具：" + name);
        };
    }

    private Map<String, Object> knowledgeSearch(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "用户问题不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("references", knowledgeSearchToolService.search(question).stream()
                .map(this::reference)
                .toList());
        return result;
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

    private Map<String, Object> reference(GuideReference reference) {
        Map<String, Object> referenceMap = new LinkedHashMap<>();
        referenceMap.put("fragmentId", reference.getFragmentId());
        referenceMap.put("documentId", reference.getDocumentId());
        referenceMap.put("goodsId", reference.getGoodsId());
        referenceMap.put("documentType", reference.getDocumentType());
        referenceMap.put("knowledgeVersion", reference.getKnowledgeVersion());
        referenceMap.put("content", reference.getContent());
        referenceMap.put("rank", reference.getRank());
        return referenceMap;
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

    private Map<String, Object> orderStatus(String orderId, String question, String userId) {
        OrderDeltaDTO orderDelta = StringUtils.hasText(orderId)
                ? orderStatusToolService.queryOrderStatus(orderId, StringUtils.hasText(userId) ? userId : null)
                : orderStatusToolService.queryOrderStatusByQuestion(question, StringUtils.hasText(userId) ? userId : null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderDelta.getOrderNo());
        result.put("tradeType", orderDelta.getTradeType());
        result.put("orderStatus", orderDelta.getCurrentStatus());
        result.put("displayStatus", orderDelta.getDisplayStatus());
        result.put("message", orderDelta.getMessage());
        return result;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        return tool(name, description, properties, properties.keySet().stream().toList());
    }

    private Map<String, Object> tool(String name,
                                     String description,
                                     Map<String, Object> properties,
                                     List<String> requiredArguments) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", requiredArguments == null ? List.of() : requiredArguments);
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
