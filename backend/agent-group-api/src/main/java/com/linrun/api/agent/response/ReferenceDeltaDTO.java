package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:27
 */
@Data
public class ReferenceDeltaDTO implements Serializable {

    private String fragmentId;
    private String documentId;
    private String goodsId;
    private String documentType;
    private String knowledgeVersion;
    private String content;
    private Integer rank;
}

