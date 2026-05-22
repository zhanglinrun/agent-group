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
                List.of("question"), List.of("limit")));
        register(new AgentToolDefinition(GUIDE_RECOMMEND, "根据用户需求生成商品推荐和推荐理由",
                List.of("question"), List.of()));
        register(new AgentToolDefinition(GROUP_TRIAL, "按商品编号查询当前拼团价、成团人数和剩余时间",
                List.of("goodsId"), List.of()));
        register(new AgentToolDefinition(ORDER_STATUS, "按订单问题查询交易订单和支付状态",
                List.of("question"), List.of("orderId")));
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

    public AgentPlan validate(AgentPlan plan) {
        if (plan == null || plan.getTools().isEmpty()) {
            throw new AppException("AGENT_0002", "工具计划不能为空");
        }
        for (AgentToolCall tool : plan.getTools()) {
            AgentToolDefinition definition = requireDefinition(tool.getName());
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
