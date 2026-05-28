package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentDTO implements Serializable {

    private String documentId;
    private String documentName;
    private String documentType;
    private String knowledgeVersion;
    private String sourceType;
    private String sourceName;
    private String documentStatus;
    private Boolean enabled;
    private Integer fragmentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
