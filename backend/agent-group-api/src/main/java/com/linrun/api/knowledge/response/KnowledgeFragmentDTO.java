package com.linrun.api.knowledge.response;

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
    private String fragmentStatus;
}
