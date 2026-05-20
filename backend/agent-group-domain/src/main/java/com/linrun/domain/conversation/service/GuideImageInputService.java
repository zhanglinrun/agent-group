package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideImageRecognitionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GuideImageInputService {

    private final List<GuideImageRecognitionClient> recognitionClients;

    public GuideImageInputService() {
        this(List.of());
    }

    @Autowired
    public GuideImageInputService(List<GuideImageRecognitionClient> recognitionClients) {
        this.recognitionClients = recognitionClients == null ? List.of() : recognitionClients;
    }

    public String parseImage(String imageUrl) {
        return parseImage(imageUrl, "");
    }

    public String parseImage(String imageUrl, String imageName) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }
        for (GuideImageRecognitionClient recognitionClient : recognitionClients) {
            String recognized = recognitionClient.recognize(imageUrl);
            if (StringUtils.hasText(recognized)) {
                return recognized;
            }
        }

        String source = summarizeSource(imageUrl, imageName);
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
        if (imageUrl.startsWith("data:image/")) {
            clues.add("图片已以内联数据传入，可在配置视觉模型后直接识别图片内容");
        }
        return String.join("；", clues) + "。图片来源：" + source;
    }

    private String summarizeSource(String imageUrl, String imageName) {
        String source = StringUtils.hasText(imageName) ? imageName.trim() : imageUrl.trim();
        if (source.startsWith("data:image/")) {
            return inlineImageSource(source);
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

    private String inlineImageSource(String source) {
        int semicolonIndex = source.indexOf(';');
        String mimeType = semicolonIndex > 0 ? source.substring("data:".length(), semicolonIndex) : "image/*";
        int commaIndex = source.indexOf(',');
        int payloadLength = commaIndex > 0 ? Math.max(0, source.length() - commaIndex - 1) : 0;
        return "内联图片数据（" + mimeType + "，约" + payloadLength + "字符）";
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
