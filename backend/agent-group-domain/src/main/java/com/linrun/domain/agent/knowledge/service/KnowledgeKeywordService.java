package com.linrun.domain.agent.knowledge.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeKeywordService {

    private static final List<String> BUSINESS_KEYWORDS = List.of(
            "预算",
            "便宜",
            "价格",
            "拼团",
            "成团",
            "退款",
            "退货",
            "售后",
            "保修",
            "质保",
            "网课",
            "学习",
            "论文",
            "轻办公",
            "文档编辑",
            "笔记",
            "手写",
            "剪视频",
            "剪辑",
            "绘图",
            "大型应用",
            "创作",
            "性能",
            "高刷",
            "轻薄",
            "便携",
            "对比",
            "标准版",
            "高配",
            "不建议",
            "直接购买",
            "直接买",
            "商品卡片",
            "导购卡片",
            "订单金额",
            "导购报价凭证",
            "导购决策编号",
            "导购判断规则",
            "依据",
            "活动过期",
            "过期",
            "隔很久",
            "队伍已满",
            "库存",
            "锁单",
            "支付成功",
            "支付平台",
            "支付单",
            "回调",
            "重复",
            "幂等",
            "防重放",
            "补偿",
            "outbox",
            "键盘",
            "会议",
            "通勤",
            "儿童",
            "家长管控",
            "护眼",
            "游戏",
            "影音",
            "追剧",
            "考研",
            "批注",
            "耗材",
            "大型游戏",
            "办公套装",
            "三年不卡",
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
        for (String word : normalized.split("[\\s,，。！？!?；;、]+")) {
            if (word.length() >= 2 && word.length() <= 20) {
                keywords.add(word);
            }
        }
        return new ArrayList<>(keywords).stream()
                .limit(12)
                .toList();
    }
}
