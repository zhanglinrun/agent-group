package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class KnowledgeFragmentDTO implements Serializable {

    private String fragmentId;
    private String documentId;
    private String goodsId;
    private String documentType;
    private String knowledgeVersion;
    private String content;
    private Integer rankNo;
    private String parentFragmentId;
    private String brotherGroupId;
    private Integer brotherIndex;
    private Integer brotherTotal;
    private String chunkType;
    private Boolean embeddingEnabled;
    private String fragmentStatus;
}















