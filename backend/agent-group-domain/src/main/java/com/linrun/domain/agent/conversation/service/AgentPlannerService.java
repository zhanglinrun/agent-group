package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.AgentSkill;
import com.linrun.domain.agent.conversation.model.AgentToolCall;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.support.config.service.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentPlannerService {

    public static final String RECOMMENDED_GOODS_ID_PLACEHOLDER = "${recommendedGoodsId}";
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("[OP]\\d{4,}");

    private final GuideDecisionService guideDecisionService;
    private final AgentToolRegistry agentToolRegistry;
    private final AgentSkillRegistry agentSkillRegistry;
    private final AgentPlanExecuteService agentPlanExecuteService;
    private final DynamicConfigService dynamicConfigService;

    public AgentPlannerService(GuideDecisionService guideDecisionService, AgentToolRegistry agentToolRegistry) {
        this(guideDecisionService, agentToolRegistry, new AgentSkillRegistry(), new AgentPlanExecuteService(), null);
    }

    @Autowired
    public AgentPlannerService(GuideDecisionService guideDecisionService,
                               AgentToolRegistry agentToolRegistry,
                               AgentSkillRegistry agentSkillRegistry,
                               AgentPlanExecuteService agentPlanExecuteService,
                               DynamicConfigService dynamicConfigService) {
        this.guideDecisionService = guideDecisionService;
        this.agentToolRegistry = agentToolRegistry;
        this.agentSkillRegistry = agentSkillRegistry == null ? new AgentSkillRegistry() : agentSkillRegistry;
        this.agentPlanExecuteService = agentPlanExecuteService == null ? new AgentPlanExecuteService() : agentPlanExecuteService;
        this.dynamicConfigService = dynamicConfigService;
    }

    public AgentPlan plan(String question) {
        GuideIntent intent = guideDecisionService.recognizeIntent(question);
        AgentPlan plan = new AgentPlan();
        plan.setIntent(intent.getIntentType());
        plan.setTools(buildTools(question, intent));
        plan.setAnswerPolicy(answerPolicy(intent.getIntentType()));
        AgentPlan validatedPlan = agentToolRegistry.validate(plan);
        List<AgentSkill> skills = agentSkillRegistry.select(intent.getIntentType(), question);
        if (dynamicConfigService == null || dynamicConfigService.isAgentPlanExecuteOpen()) {
            return agentPlanExecuteService.enrich(question, validatedPlan, skills);
        }
        validatedPlan.setSkills(skills);
        return validatedPlan;
    }

    private List<AgentToolCall> buildTools(String question, GuideIntent intent) {
        List<AgentToolCall> tools = new ArrayList<>();
        if (GuideIntentType.ORDER_QUERY.equals(intent.getIntentType())) {
            tools.add(AgentToolCall.of(AgentToolRegistry.ORDER_STATUS,
                    arguments("question", question, "orderId", extractOrderId(question)),
                    "订单状态必须查询交易系统，不能由模型编造。"));
            return tools;
        }

        tools.add(AgentToolCall.of(AgentToolRegistry.KNOWLEDGE_SEARCH,
                arguments("question", question, "limit", "3"),
                "先检索商品详情、活动规则和售后政策，保证回答有依据。"));
        tools.add(AgentToolCall.of(AgentToolRegistry.GUIDE_RECOMMEND,
                arguments("question", question),
                "结合用户预算、场景和知识片段完成商品排序。"));
        if (shouldTrialGroup(question, intent)) {
            tools.add(AgentToolCall.of(AgentToolRegistry.GROUP_TRIAL,
                    arguments("goodsId", RECOMMENDED_GOODS_ID_PLACEHOLDER),
                    "涉及价格或拼团时，必须用后端活动服务试算真实拼团信息。"));
        }
        return tools;
    }

    public AgentPlan fillRuntimeArguments(AgentPlan plan, GuideDecisionResult decisionResult) {
        if (plan == null || decisionResult == null || decisionResult.getProduct() == null
                || !StringUtils.hasText(decisionResult.getProduct().getGoodsId())) {
            return plan;
        }
        for (AgentToolCall tool : plan.getTools()) {
            if (AgentToolRegistry.GROUP_TRIAL.equals(tool.getName())
                    && RECOMMENDED_GOODS_ID_PLACEHOLDER.equals(tool.getArguments().get("goodsId"))) {
                tool.getArguments().put("goodsId", decisionResult.getProduct().getGoodsId());
            }
        }
        return plan;
    }

    public boolean hasRuntimePlaceholder(AgentPlan plan) {
        if (plan == null) {
            return false;
        }
        return plan.getTools().stream()
                .anyMatch(tool -> tool.getArguments().containsValue(RECOMMENDED_GOODS_ID_PLACEHOLDER));
    }

    private boolean shouldTrialGroup(String question, GuideIntent intent) {
        String normalized = question == null ? "" : question.toLowerCase();
        return intent.isGroupBuyConcerned()
                || intent.isBudgetSensitive()
                || intent.isCompareConcerned()
                || containsAny(normalized,
                "价格", "报价", "优惠", "省钱", "划算", "直接买", "直接购买", "原价",
                "锁单", "下单", "支付", "支付单", "订单金额", "商品卡片", "金额", "凭证", "决策编号",
                "活动过期", "过期", "下架", "库存", "名额", "队伍", "满了", "队伍已满",
                "幂等", "重复", "重复点", "重复下单", "防重放", "回调", "补偿", "outbox",
                "儿童", "小孩", "家长管控", "护眼", "考研", "配件", "一次配齐", "办公套装", "键盘套装");
    }

    private String answerPolicy(GuideIntentType intentType) {
        if (GuideIntentType.ORDER_QUERY.equals(intentType)) {
            return "只基于订单工具结果回答订单、支付和退款状态，不编造状态。";
        }
        return "基于知识检索、商品推荐和拼团试算结果回答；价格、库存、活动和售后规则必须来自工具结果。";
    }

    private Map<String, String> arguments(String... pairs) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (StringUtils.hasText(pairs[i + 1])) {
                arguments.put(pairs[i], pairs[i + 1]);
            }
        }
        return arguments;
    }

    private String extractOrderId(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        Matcher matcher = ORDER_ID_PATTERN.matcher(question);
        return matcher.find() ? matcher.group() : "";
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
