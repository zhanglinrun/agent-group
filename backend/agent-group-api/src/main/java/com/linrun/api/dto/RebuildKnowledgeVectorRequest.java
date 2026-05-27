package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RebuildKnowledgeVectorRequest implements Serializable {

    private String knowledgeVersion;
}
