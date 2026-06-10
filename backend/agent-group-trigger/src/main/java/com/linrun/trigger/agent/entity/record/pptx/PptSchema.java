package com.linrun.trigger.agent.entity.record.pptx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PPT Schema数据结构
 * 对应文档中的JSON Schema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptSchema {

    /**
     * 幻灯片列??
     */
    private List<Slide> slides;
}















