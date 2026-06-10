package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.prompt.adapter.PromptTemplateRepository;
import com.linrun.domain.agent.prompt.model.PromptTemplate;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class LocalPromptTemplateRepository implements PromptTemplateRepository {

    private final List<PromptTemplate> templates = List.of(
            PromptTemplate.enabled("PT-GUIDE-001", PromptTemplateType.GUIDE, "guide-v1.0", """
                    你是额度购买与智能体使用助手。回答必须只基于额度包资料、拼团试算和知识片段；信息不足时要明确说明待补充，不要编造??                    输出要适合流式展示，先给结论，再给后端工具结果和知识依据，最后给购买、到账或售后提醒??                    价格、库存、活动、订单金额、退款和支付状态属于高风险信息，必须以后端工具或交易系统结果为准，不能只按模型推测??                    """),
            PromptTemplate.enabled("PT-RULE-001", PromptTemplateType.RULE_QA, "rule-v1.0", """
                    规则问答只回答知识片段中已经出现的规则。涉及退款、售后、成团人数、活动时间时，必须说明依据来自哪个片段??                    """),
            PromptTemplate.enabled("PT-REASON-001", PromptTemplateType.RECOMMEND_REASON, "reason-v1.0", """
                    推荐理由需要覆盖用户身份、使用场景、预算、额度数量、原价、拼团价、成团人数、售后风险和不适合场景，不能只给单一价格结论??                    """),
            PromptTemplate.enabled("PT-CHECK-001", PromptTemplateType.SELF_CHECK, "self-check-v1.0", """
                    回答前检查：额度包编号、额度包名称、原价、拼团价、成团人数、售后政策、知识片段和边界结论是否齐全；缺失时直接提示资料待补充??                    """)
    );

    @Override
    public Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType) {
        return templates.stream()
                .filter(template -> Boolean.TRUE.equals(template.getEnabled()))
                .filter(template -> template.getTemplateType().equals(templateType))
                .max(Comparator.comparing(PromptTemplate::getTemplateVersion));
    }

    @Override
    public List<PromptTemplate> queryEnabledTemplates() {
        return templates.stream()
                .filter(template -> Boolean.TRUE.equals(template.getEnabled()))
                .toList();
    }
}















