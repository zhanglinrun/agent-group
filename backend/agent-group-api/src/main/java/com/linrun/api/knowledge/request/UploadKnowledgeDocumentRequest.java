package com.linrun.api.knowledge.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class UploadKnowledgeDocumentRequest implements Serializable {

    private String documentName;
    private String documentType;
    private String knowledgeVersion;
    private String sourceType;
    private String sourceName;
    private String goodsId;
    private String content;
}
