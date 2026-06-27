package com.linrun.domain.agent.conversation.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuotaProductKeywordService {

    private static final List<String> BUSINESS_KEYWORDS = List.of(
            "预算",
            "便宜",
            "价格",
            "拼团",
            "成团",
            "退款",
            "退费",
            "售后",
            "额度",
            "余额",
            "消费",
            "Agent",
            "论文",
            "文献",
            "PDF",
            "精读",
            "PPT",
            "汇报",
            "答辩",
            "图表",
            "流程图",
            "架构图",
            "Mermaid",
            "深度研究",
            "调研",
            "技术路线",
            "长报告",
            "复现",
            "团队",
            "实验",
            "对比",
            "基础额度包",
            "长文档额度包",
            "PPT 创作额度包",
            "图表重建额度包",
            "深度任务额度包",
            "团队拼团额度包",
            "不确定",
            "直接购买",
            "额度包",
            "订单金额",
            "支付单金额",
            "前端金额",
            "价格篡改",
            "依据",
            "活动过期",
            "过期",
            "隔离",
            "队伍已满",
            "库存",
            "锁单",
            "支付成功",
            "支付平台",
            "支付状态",
            "回调",
            "重复",
            "幂等",
            "防重放",
            "补偿",
            "outbox",
            "不能保证",
            "成团结算"
    );

    public List<String> extractKeywords(String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        String normalized = question.trim().toLowerCase();
        Set<String> keywords = new LinkedHashSet<>();
        BUSINESS_KEYWORDS.stream()
                .filter(normalized::contains)
                .forEach(keywords::add);
        for (String word : normalized.split("[\\s,，。！）?）、]+")) {
            if (word.length() >= 2 && word.length() <= 20) {
                keywords.add(word);
            }
        }
        return new ArrayList<>(keywords).stream()
                .limit(12)
                .toList();
    }
}
