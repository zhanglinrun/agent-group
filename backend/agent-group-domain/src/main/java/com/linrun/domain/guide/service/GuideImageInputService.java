package com.linrun.domain.guide.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GuideImageInputService {

    public String parseImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }

        String source = summarizeSource(imageUrl);
        String normalized = source.toLowerCase(Locale.ROOT);
        List<String> clues = new ArrayList<>();
        if (containsAny(normalized, "pad", "tablet", "ipad", "平板")) {
            clues.add("图片疑似平板商品或商品截图");
        }
        if (containsAny(normalized, "course", "study", "student", "class", "网课", "学习", "学生")) {
            clues.add("图片可能包含学习或网课使用场景");
        }
        if (containsAny(normalized, "price", "discount", "group", "coupon", "拼团", "优惠", "价格")) {
            clues.add("图片可能包含价格、优惠或拼团信息");
        }
        if (clues.isEmpty()) {
            clues.add("已收到用户上传图片，需要结合图片中的商品外观、规格、价格或页面信息进行判断");
        }
        return String.join("；", clues) + "。图片来源：" + source;
    }

    private String summarizeSource(String imageUrl) {
        String source = imageUrl.trim();
        if (source.startsWith("data:image/")) {
            return "内联图片数据";
        }
        int index = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        if (index >= 0 && index + 1 < source.length()) {
            source = source.substring(index + 1);
        }
        if (source.length() <= 120) {
            return source;
        }
        return source.substring(0, 120);
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
