package com.linrun.api.knowledge.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class BackupKnowledgeVectorRequest implements Serializable {

    private String knowledgeVersion;
}
