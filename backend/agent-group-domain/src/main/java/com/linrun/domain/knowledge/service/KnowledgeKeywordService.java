package com.linrun.domain.knowledge.service;

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
            "笔记",
            "手写",
            "剪辑",
            "绘图",
            "对比",
            "标准版",
            "高配"
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
                .limit(8)
                .toList();
    }
}
