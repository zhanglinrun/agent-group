package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ExecuteCrowdTagJobRequest implements Serializable {

    private String tagId;
    private String batchId;
}















