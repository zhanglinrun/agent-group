package com.linrun.trigger.agent.entity;

import com.linrun.trigger.agent.common.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 幻灯片图片信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideImage {

    /**
     * 页码
     */
    private Integer page;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 图片提示词（用于生成图片）
     */
    private String imagePrompt;

    /**
     * 图片在页面中的位置（可选，"顶部"、"底部"、"左侧"、"右侧")
     */
    private String position;

    /**
     * 图片描述（可选）
     */
    private String description;

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }

    /**
     * 从JSON字符串创建对象
     */
    public static SlideImage fromJson(String json) {
        return JsonUtils.parseValue(json, SlideImage.class);
    }

    /**
     * 转换为List<SlideImage>
     */
    public static List<SlideImage> fromJsonList(String json) {
        return JsonUtils.parseList(json, SlideImage.class);
    }

    /**
     * 转换为JSON字符串
     */
    public static String toJson(List<SlideImage> images) {
        return JsonUtils.toJson(images);
    }
}















