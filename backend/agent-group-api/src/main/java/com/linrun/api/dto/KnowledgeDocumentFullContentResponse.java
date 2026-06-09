package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeDocumentFullContentResponse implements Serializable {

    private String documentId;
    private String documentName;
    private String documentType;
    private String knowledgeVersion;
    private String sourceType;
    private String sourceName;
    private String documentStatus;
    private Boolean enabled;
    private Integer fragmentCount;
    private String content;
    private List<KnowledgeFragmentDTO> fragments = new ArrayList<>();
}















