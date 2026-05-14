package com.linrun.domain.knowledge.adapter;

import com.linrun.domain.knowledge.model.StoredKnowledgeObject;

public interface KnowledgeObjectStorageClient {

    StoredKnowledgeObject store(String originalFilename, String contentType, byte[] content);
}
