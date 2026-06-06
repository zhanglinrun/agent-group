package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.AgentSkill;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentSkillRegistry {

    private static final String QUOTA_PACKAGE_ADVICE = "quota_package_advice";
    private static final String AFTER_SALE_POLICY = "after_sale_policy";
    private static final String TRADE_RISK_CONTROL = "trade_risk_control";

    private final Map<String, AgentSkill> skills = new LinkedHashMap<>();

    public AgentSkillRegistry() {
        register(new AgentSkill(
                QUOTA_PACKAGE_ADVICE,
                "额度包建议技能",
                "把用户任务转成额度包候选、知识依据和推荐理由",
                "价格、名额和活动口径只能来自工具结果",
                List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.QUOTA_RECOMMEND, AgentToolRegistry.GROUP_TRIAL)));
        register(new AgentSkill(
                AFTER_SALE_POLICY,
                "售后解释技能",
                "解释退货、退款、保修和未成团处理规则",
                "售后规则必须引用知识库或订单工具结果",
                List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.ORDER_STATUS)));
        register(new AgentSkill(
                TRADE_RISK_CONTROL,
                "交易风控技能",
                "识别支付、订单、拼团状态和幂等风险",
                "交易状态必须以订单系统和拼团试算为准",
                List.of(AgentToolRegistry.GROUP_TRIAL, AgentToolRegistry.ORDER_STATUS)));
    }

    public List<AgentSkill> select(GuideIntentType intentType, String question) {
        List<AgentSkill> selected = new ArrayList<>();
        if (GuideIntentType.ORDER_QUERY.equals(intentType)) {
            selected.add(skills.get(TRADE_RISK_CONTROL));
            return selected;
        }
        if (containsAny(question, "售后", "退款", "退货", "保修", "未成团", "失败")) {
            selected.add(skills.get(AFTER_SALE_POLICY));
        }
        if (containsAny(question, "支付", "下单", "订单", "锁单", "拼团", "成团", "库存", "名额")) {
            selected.add(skills.get(TRADE_RISK_CONTROL));
        }
        selected.add(0, skills.get(QUOTA_PACKAGE_ADVICE));
        return selected.stream().distinct().toList();
    }

    private void register(AgentSkill skill) {
        skills.put(skill.getSkillId(), skill);
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
