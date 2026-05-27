package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UploadKnowledgeDocumentResponse implements Serializable {

    private String documentId;
    private String documentName;
    private String documentType;
    private String knowledgeVersion;
    private String sourceType;
    private String sourceName;
    private String objectStorageBucket;
    private String objectKey;
    private String objectUrl;
    private String contentType;
    private Long objectSize;
    private String documentStatus;
    private Integer fragmentCount;
    private LocalDateTime createTime;
    private List<KnowledgeFragmentDTO> fragments = new ArrayList<>();
}
