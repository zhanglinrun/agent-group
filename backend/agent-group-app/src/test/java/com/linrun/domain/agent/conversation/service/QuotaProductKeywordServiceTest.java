package com.linrun.domain.agent.conversation.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaProductKeywordServiceTest {

    @Test
    void shouldExtractBusinessKeywords() {
        QuotaProductKeywordService service = new QuotaProductKeywordService();
        List<String> keywords = service.extractKeywords("我想拼团购买基础额度包，退款规则是什么？");
        assertTrue(keywords.contains("拼团"));
        assertTrue(keywords.contains("退款"));
        assertTrue(keywords.contains("基础额度包"));
    }

    @Test
    void shouldReturnEmptyForBlankQuestion() {
        QuotaProductKeywordService service = new QuotaProductKeywordService();
        assertTrue(service.extractKeywords("").isEmpty());
        assertTrue(service.extractKeywords(null).isEmpty());
    }

    @Test
    void shouldLimitKeywordCount() {
        QuotaProductKeywordService service = new QuotaProductKeywordService();
        List<String> keywords = service.extractKeywords(
                "拼团 退款 额度 余额 支付成功 回调 幂等 补偿 outbox 成团 售后 价格 预算 便宜");
        assertFalse(keywords.size() > 12);
    }
}
