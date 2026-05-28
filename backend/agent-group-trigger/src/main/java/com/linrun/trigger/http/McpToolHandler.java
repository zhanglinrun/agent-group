package com.linrun.trigger.http;




import com.linrun.domain.trade.service.OrderStatusToolService;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.trigger.support.tool.ToolExecution;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.CreatePayRequest;
import com.linrun.api.dto.MallProductDTO;
import com.linrun.api.dto.OrderDeltaDTO;
import com.linrun.api.dto.RefundOrderRequest;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideQueryRoute;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.KnowledgeSearchResult;
import com.linrun.domain.agent.conversation.model.RecommendationResult;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.agent.conversation.service.GuideIntentRecognitionService;
import com.linrun.domain.agent.conversation.service.GuideDecisionService;
import com.linrun.domain.agent.conversation.service.KnowledgeSearchToolService;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.trigger.support.json.JsonRepairUtil;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpToolHandler {

    public static final String QUERY_ROUTE = "query_route";
    public static final String REFUND_STATUS = "refund_status";
    public static final String JSON_REPAIR = "json_repair";
    public static final String DOCUMENT_COMPENSATION = "document_compensation";
    public static final String INTENT_RECOGNITION = "intent_recognition";
    public static final String PRODUCT_CATALOG = "product_catalog";
    public static final String PRODUCT_DETAIL = "product_detail";
    public static final String CREATE_PAY_ORDER = "create_pay_order";
    public static final String REFUND_ORDER = "refund_order";

    private final GuideDecisionService guideDecisionService;
    private final KnowledgeSearchToolService knowledgeSearchToolService;
    private final GuideIntentRecognitionService guideIntentRecognitionService;
    private final GroupBuyActivityService groupBuyActivityService;
    private final OrderStatusToolService orderStatusToolService;
    private final KnowledgeVectorOpsHandler knowledgeVectorOpsService;
    private final MallProductCatalogHandler mallProductCatalogHandler;
    private final LegacyMallPayHandler legacyMallPayHandler;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public McpToolHandler(GuideDecisionService guideDecisionService,
                          KnowledgeSearchToolService knowledgeSearchToolService,
                          GuideIntentRecognitionService guideIntentRecognitionService,
                          GroupBuyActivityService groupBuyActivityService,
                          OrderStatusToolService orderStatusToolService,
                          KnowledgeVectorOpsHandler knowledgeVectorOpsService,
                          MallProductCatalogHandler mallProductCatalogHandler,
                          LegacyMallPayHandler legacyMallPayHandler,
                          ToolExecutor toolExecutor,
                          ObjectMapper objectMapper) {
        this.guideDecisionService = guideDecisionService;
        this.knowledgeSearchToolService = knowledgeSearchToolService;
        this.guideIntentRecognitionService = guideIntentRecognitionService;
        this.groupBuyActivityService = groupBuyActivityService;
        this.orderStatusToolService = orderStatusToolService;
        this.knowledgeVectorOpsService = knowledgeVectorOpsService;
        this.mallProductCatalogHandler = mallProductCatalogHandler;
        this.legacyMallPayHandler = legacyMallPayHandler;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                tool(INTENT_RECOGNITION, "Recognize structured intent, order id, goods id, budget, and scenario slots.",
                        Map.of("question", stringSchema("User question."))),
                tool(QUERY_ROUTE, "Route a user question to trade data, market data, knowledge base, or hybrid retrieval.",
                        Map.of("question", stringSchema("User question."))),
                tool(AgentToolRegistry.KNOWLEDGE_SEARCH, "Search product, campaign, and after-sale knowledge fragments.",
                        Map.of("question", stringSchema("User question."))),
                tool(AgentToolRegistry.GUIDE_RECOMMEND, "Return guide recommendation, product card, and evidence.",
                        Map.of("question", stringSchema("User question."))),
                tool(PRODUCT_CATALOG, "List mall products with current group-buy campaign fields.",
                        Map.of(
                                "keyword", stringSchema("Optional product search keyword."),
                                "limit", integerSchema("Max products to return.")),
                        List.of()),
                tool(PRODUCT_DETAIL, "Return a mall product detail with current group-buy campaign fields.",
                        Map.of("goodsId", stringSchema("Goods id."))),
                tool(AgentToolRegistry.GROUP_TRIAL, "Return group-buy trial result by goods id.",
                        Map.of("goodsId", stringSchema("Goods id."))),
                tool(CREATE_PAY_ORDER, "Create a direct-buy or group-buy pay order through the trade facade.",
                        Map.of(
                                "userId", stringSchema("User id."),
                                "productId", stringSchema("Product id."),
                                "marketType", integerSchema("0 for direct buy, 1 for group buy."),
                                "activityId", stringSchema("Activity id for group buy."),
                                "decisionId", stringSchema("Guide decision id."),
                                "teamId", stringSchema("Optional team id.")),
                        List.of("userId", "productId", "marketType")),
                tool(AgentToolRegistry.ORDER_STATUS, "Return trade order and payment status.",
                        Map.of(
                                "orderId", stringSchema("Order id."),
                                "question", stringSchema("Original order question."),
                                "userId", stringSchema("User id.")),
                        List.of()),
                tool(REFUND_STATUS, "Return refund status by order id.",
                        Map.of(
                                "orderId", stringSchema("Order id."),
                                "userId", stringSchema("User id.")),
                        List.of("orderId")),
                tool(REFUND_ORDER, "Refund or close a mall order by order id.",
                        Map.of(
                                "orderId", stringSchema("Order id."),
                                "userId", stringSchema("User id."),
                                "refundReason", stringSchema("Refund reason.")),
                        List.of("orderId")),
                tool(JSON_REPAIR, "Repair model generated JSON text into parseable JSON.",
                        Map.of("text", stringSchema("Raw JSON text."))),
                tool(DOCUMENT_COMPENSATION, "Retry failed document embedding tasks.",
                        Map.of("limit", integerSchema("Max documents to compensate.")),
                        List.of()));
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        ToolExecution<Map<String, Object>> execution = toolExecutor.execute(
                "mcp." + name,
                "tools/call",
                "tool call completed",
                () -> executeTool(name, arguments == null ? Map.of() : arguments));
        if (execution.isSuccess()) {
            return toolResult(false, execution.getResult());
        }
        return toolResult(true, Map.of("message", execution.getMessage()));
    }

    private Map<String, Object> executeTool(String name, Map<String, Object> arguments) {
        return switch (name) {
            case INTENT_RECOGNITION -> intentRecognition(text(arguments.get("question")));
            case QUERY_ROUTE -> queryRoute(text(arguments.get("question")));
            case AgentToolRegistry.KNOWLEDGE_SEARCH -> knowledgeSearch(text(arguments.get("question")));
            case AgentToolRegistry.GUIDE_RECOMMEND -> guideRecommend(text(arguments.get("question")));
            case PRODUCT_CATALOG -> productCatalog(text(arguments.get("keyword")), integer(arguments.get("limit"), 20));
            case PRODUCT_DETAIL -> productDetail(text(arguments.get("goodsId")));
            case AgentToolRegistry.GROUP_TRIAL -> groupTrial(text(arguments.get("goodsId")));
            case CREATE_PAY_ORDER -> createPayOrder(arguments);
            case AgentToolRegistry.ORDER_STATUS -> orderStatus(
                    text(arguments.get("orderId")),
                    text(arguments.get("question")),
                    text(arguments.get("userId")));
            case REFUND_STATUS -> refundStatus(text(arguments.get("orderId")), text(arguments.get("userId")));
            case REFUND_ORDER -> refundOrder(arguments);
            case JSON_REPAIR -> jsonRepair(text(arguments.get("text")));
            case DOCUMENT_COMPENSATION -> documentCompensation(integer(arguments.get("limit"), 20));
            default -> throw new AppException("MCP_0001", "unknown tool: " + name);
        };
    }

    private Map<String, Object> intentRecognition(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        GuideIntent intent = guideIntentRecognitionService.recognize(question);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intentType", intent.getIntentType().name());
        result.put("orderId", nullToBlank(intent.getOrderId()));
        result.put("goodsId", nullToBlank(intent.getGoodsId()));
        result.put("budgetUpperLimit", intent.getBudgetUpperLimit());
        result.put("userIdentity", nullToBlank(intent.getUserIdentity()));
        result.put("usageScenarios", intent.getUsageScenarios());
        result.put("entities", intent.getEntities());
        result.put("flags", Map.of(
                "budgetSensitive", intent.isBudgetSensitive(),
                "groupBuyConcerned", intent.isGroupBuyConcerned(),
                "afterSaleConcerned", intent.isAfterSaleConcerned(),
                "compareConcerned", intent.isCompareConcerned(),
                "performanceSensitive", intent.isPerformanceSensitive(),
                "portabilitySensitive", intent.isPortabilitySensitive()));
        return result;
    }

    private Map<String, Object> queryRoute(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        return route(knowledgeSearchToolService.route(question));
    }

    private Map<String, Object> knowledgeSearch(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        KnowledgeSearchResult searchResult = knowledgeSearchToolService.searchWithRoute(question, 3);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("route", route(searchResult.getRoute()));
        result.put("references", searchResult.getReferences().stream().map(this::reference).toList());
        return result;
    }

    private Map<String, Object> guideRecommend(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
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
        result.put("references", decisionResult.getReferences().stream().map(this::reference).toList());
        return result;
    }

    private Map<String, Object> productCatalog(String keyword, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", mallProductCatalogHandler.queryProductOptions(keyword, limit).stream()
                .map(this::product)
                .toList());
        return result;
    }

    private Map<String, Object> productDetail(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
        return product(mallProductCatalogHandler.queryProductDetail(goodsId));
    }

    private Map<String, Object> product(MallProductDTO product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsId", product.getGoodsId());
        result.put("goodsName", product.getGoodsName());
        result.put("imageUrl", nullToBlank(product.getImageUrl()));
        result.put("originPrice", product.getOriginPrice());
        result.put("groupPrice", product.getGroupPrice());
        result.put("activityId", nullToBlank(product.getActivityId()));
        result.put("teamSize", product.getTeamSize());
        result.put("remainingSeconds", product.getRemainingSeconds());
        result.put("groupBuyAvailable", product.isGroupBuyAvailable());
        result.put("marketMessage", nullToBlank(product.getMarketMessage()));
        result.put("specSummary", nullToBlank(product.getSpecSummary()));
        result.put("afterSalePolicy", nullToBlank(product.getAfterSalePolicy()));
        result.put("recommendReason", nullToBlank(product.getRecommendReason()));
        result.put("notSuitableFor", nullToBlank(product.getNotSuitableFor()));
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

    private Map<String, Object> route(GuideQueryRoute route) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", route.getStrategy());
        result.put("intent", route.getIntent());
        result.put("reason", route.getReason());
        result.put("confidence", route.getConfidence());
        result.put("retrievers", route.getRetrievers());
        return result;
    }

    private Map<String, Object> groupTrial(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "goodsId cannot be blank");
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

    private Map<String, Object> refundStatus(String orderId, String userId) {
        return orderStatusToolService.queryRefundStatus(orderId, StringUtils.hasText(userId) ? userId : null);
    }

    private Map<String, Object> createPayOrder(Map<String, Object> arguments) {
        CreatePayRequest request = new CreatePayRequest();
        request.setUserId(text(arguments.get("userId")));
        request.setProductId(text(arguments.get("productId")));
        request.setMarketType(integer(arguments.get("marketType"), 0));
        request.setActivityId(text(arguments.get("activityId")));
        request.setDecisionId(text(arguments.get("decisionId")));
        request.setTeamId(text(arguments.get("teamId")));
        request.setPayChannel(text(arguments.get("payChannel")));
        request.setIdempotentKey(text(arguments.get("idempotentKey")));
        String payUrl = legacyMallPayHandler.createPayOrder(request);
        return Map.of("payUrl", payUrl);
    }

    private Map<String, Object> refundOrder(Map<String, Object> arguments) {
        RefundOrderRequest request = new RefundOrderRequest();
        request.setOrderId(text(arguments.get("orderId")));
        request.setUserId(text(arguments.get("userId")));
        request.setRefundReason(text(arguments.get("refundReason")));
        var response = legacyMallPayHandler.refundOrder(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.isSuccess());
        result.put("orderId", response.getOrderId());
        result.put("message", response.getMessage());
        return result;
    }

    private Map<String, Object> jsonRepair(String text) {
        String fixed = JsonRepairUtil.repair(text);
        return Map.of(
                "valid", JsonRepairUtil.isValid(fixed),
                "json", fixed);
    }

    private Map<String, Object> documentCompensation(int limit) {
        var response = knowledgeVectorOpsService.compensateFailedEmbedding(limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", response.getAction());
        result.put("fragmentCount", response.getFragmentCount());
        result.put("successCount", response.getSuccessCount());
        result.put("failedCount", response.getFailedCount());
        result.put("hitFragmentIds", response.getHitFragmentIds());
        result.put("message", response.getMessage());
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

    private Map<String, Object> integerSchema(String description) {
        return Map.of("type", "integer", "description", description);
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

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
