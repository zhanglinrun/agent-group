package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CrowdTagJobResponse implements Serializable {

    private String tagId;
    private String batchId;
    private Integer tagType;
    private String tagRule;
    private int matchedCount;
    private List<String> userIds;
    private String message;
}















