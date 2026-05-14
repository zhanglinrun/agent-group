package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideImageRecognitionClient;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void shouldKeepLocalCluesWhenInlineImageHasFileName() {
        String summary = service.parseImage("data:image/png;base64,AAAA", "student-pad-group-price.png");

        assertTrue(summary.contains("图片疑似平板商品或商品截图"));
        assertTrue(summary.contains("价格、优惠或拼团信息"));
        assertTrue(summary.contains("内联数据传入"));
        assertTrue(summary.contains("student-pad-group-price.png"));
    }

    @Test
    void shouldUseVisionClientBeforeLocalFallback() {
        GuideImageInputService visionService = new GuideImageInputService(List.of(new FakeVisionClient()));

        String summary = visionService.parseImage("https://example.com/pad.png");

        assertEquals("图片中包含平板商品和拼团价。", summary);
    }

    private static class FakeVisionClient implements GuideImageRecognitionClient {

        @Override
        public String recognize(String imageUrl) {
            return "图片中包含平板商品和拼团价。";
        }
    }
}
