package com.linrun.api.tag.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class ExecuteCrowdTagJobRequest implements Serializable {

    private String tagId;
    private String batchId;
}
