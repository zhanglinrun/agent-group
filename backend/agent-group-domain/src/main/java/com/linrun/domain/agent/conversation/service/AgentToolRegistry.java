package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.AgentToolCall;
import com.linrun.domain.agent.conversation.model.AgentToolDefinition;
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
    public static final String QUOTA_RECOMMEND = "quota_recommend";
    public static final String GROUP_TRIAL = "group_trial";
    public static final String ORDER_STATUS = "order_status";

    private final Map<String, AgentToolDefinition> definitions;

    public AgentToolRegistry() {
        this.definitions = new LinkedHashMap<>();
        register(new AgentToolDefinition(KNOWLEDGE_SEARCH, "检索额度包说明、活动规则和退款规则知识片段",
                List.of("question"), List.of("limit"), "knowledge-search-v1", "MEDIUM",
                2000L, 1, true, false));
        register(new AgentToolDefinition(QUOTA_RECOMMEND, "根据用户需求生成额度包推荐和使用依据",
                List.of("question"), List.of(), "quota-recommend-v1", "MEDIUM",
                3000L, 0, true, false));
        register(new AgentToolDefinition(GROUP_TRIAL, "按额度包编号查询当前拼团价、成团人数和剩余时间",
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
