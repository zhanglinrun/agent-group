package com.linrun.domain.agent.knowledge.adapter;

import com.linrun.domain.agent.knowledge.model.StoredKnowledgeObject;

public interface KnowledgeObjectStorageClient {

    StoredKnowledgeObject store(String originalFilename, String contentType, byte[] content);
}
