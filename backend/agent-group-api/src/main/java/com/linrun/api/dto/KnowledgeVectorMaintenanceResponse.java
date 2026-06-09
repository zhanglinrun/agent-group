package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class KnowledgeVectorMaintenanceResponse implements Serializable {

    private String action;
    private String knowledgeVersion;
    private Integer fragmentCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer expectedCount;
    private Integer matchedCount;
    private BigDecimal recallHitRate;
    private String snapshotId;
    private List<String> hitFragmentIds;
    private String message;
}















