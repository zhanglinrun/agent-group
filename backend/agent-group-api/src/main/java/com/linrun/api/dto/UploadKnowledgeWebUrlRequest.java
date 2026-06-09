package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UploadKnowledgeWebUrlRequest implements Serializable {

    private String url;
    private String goodsId;
    private String documentName;
    private String documentType;
    private String knowledgeVersion;
}















