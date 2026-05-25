package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.model.AgentPlan;
import com.linrun.domain.conversation.model.AgentToolCall;
import com.linrun.domain.conversation.model.AgentToolDefinition;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentToolRegistry {

    public static final String KNOWLEDGE_SEARCH = "knowledge_search";
    public static final String GUIDE_RECOMMEND = "guide_recommend";
    public static final String GROUP_TRIAL = "group_trial";
    public static final String ORDER_STATUS = "order_status";

    private final Map<String, AgentToolDefinition> definitions;

    public AgentToolRegistry() {
        this.definitions = new LinkedHashMap<>();
        register(new AgentToolDefinition(KNOWLEDGE_SEARCH, "检索商品详情、营销规则和售后政策知识片段",
                List.of("question"), List.of("limit"), "knowledge-search-v1", "MEDIUM",
                2000L, 1, true, false));
        register(new AgentToolDefinition(GUIDE_RECOMMEND, "根据用户需求生成商品推荐和推荐理由",
                List.of("question"), List.of(), "guide-recommend-v1", "MEDIUM",
                3000L, 0, true, false));
        register(new AgentToolDefinition(GROUP_TRIAL, "按商品编号查询当前拼团价、成团人数和剩余时间",
                List.of("goodsId"), List.of(), "group-trial-v1", "HIGH",
                1500L, 1, true, false));
        register(new AgentToolDefinition(ORDER_STATUS, "按订单问题查询交易订单和支付状态",
                List.of("question"), List.of("orderId"), "order-status-v1", "HIGH",
                1500L, 1, true, false));
    }

    public List<AgentToolDefinition> listDefinitions() {
        return definitions.values().stream().toList();
    }

    public AgentToolDefinition requireDefinition(String name) {
        AgentToolDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new AppException("AGENT_0001", "工具不在白名单内：" + name);
        }
        return definition;
    }

    public Optional<AgentToolDefinition> findDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public AgentPlan validate(AgentPlan plan) {
        if (plan == null || plan.getTools().isEmpty()) {
            throw new AppException("AGENT_0002", "工具计划不能为空");
        }
        for (AgentToolCall tool : plan.getTools()) {
            AgentToolDefinition definition = requireDefinition(tool.getName());
            tool.setToolVersion(definition.getVersion());
            tool.setRiskLevel(definition.getRiskLevel());
            tool.setResultCitationRequired(definition.isResultCitationRequired());
            for (String argumentName : definition.getRequiredArguments()) {
                String value = tool.getArguments().get(argumentName);
                if (!StringUtils.hasText(value)) {
                    throw new AppException("AGENT_0003", "工具参数缺失：" + tool.getName() + "." + argumentName);
                }
            }
        }
        return plan;
    }

    private void register(AgentToolDefinition definition) {
        definitions.put(definition.getName(), definition);
    }
}
