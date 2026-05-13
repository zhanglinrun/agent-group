package com.linrun.domain.guide.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideImageInputServiceTest {

    private final GuideImageInputService service = new GuideImageInputService();

    @Test
    void shouldReturnBlankWhenImageMissing() {
        assertEquals("", service.parseImage(" "));
    }

    @Test
    void shouldParseImageSourceIntoShoppingClues() {
        String summary = service.parseImage("local-image://student-pad-group-price.png");

        assertTrue(summary.contains("图片疑似平板商品或商品截图"));
        assertTrue(summary.contains("学习或网课使用场景"));
        assertTrue(summary.contains("价格、优惠或拼团信息"));
        assertTrue(summary.contains("student-pad-group-price.png"));
    }
}
